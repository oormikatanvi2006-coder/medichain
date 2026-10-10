package com.medichain.lite;

import com.medichain.forecast.auth.AppUserRepository;
import com.medichain.forecast.auth.AuthController;
import com.medichain.forecast.auth.AuthService;
import com.medichain.forecast.engine.AnalysisEngine;
import com.medichain.forecast.repo.RecommendationRepository;
import com.medichain.forecast.service.AnalyticsService;
import com.medichain.forecast.service.QueryService;
import com.medichain.forecast.service.RecommendationService;
import com.medichain.forecast.web.ApiModels.QueryRequest;
import com.medichain.forecast.web.ForecastController;
import com.medichain.forecast.web.InventoryGatewayController;
import com.medichain.inventory.repo.BatchRepository;
import com.medichain.inventory.repo.CaseTrendRepository;
import com.medichain.inventory.repo.ConsumptionLogRepository;
import com.medichain.inventory.repo.FacilityRepository;
import com.medichain.inventory.repo.MedicineRepository;
import com.medichain.inventory.repo.StockTransactionRepository;
import com.medichain.inventory.repo.StockTransferRepository;
import com.medichain.inventory.seed.DataSeeder;
import com.medichain.inventory.seed.LiveFeedSimulator;
import com.medichain.inventory.service.InventoryService;
import com.medichain.inventory.web.InventoryController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.web.server.ResponseStatusException;

/**
 * MediChain Lite: the dashboard (front end) and the REST API (back end) in one Java program.
 *
 * <p>It runs the same controllers, services, forecasting and recommendation code as the Spring Boot
 * project. Two things are swapped so that it needs nothing but Java: the JDK's built-in web server
 * replaces Spring's, and in-memory tables replace the H2 database.
 *
 * <pre>java -jar medichain-lite.jar [port]</pre>
 */
public final class LiteServer {
    private final ForecastController forecast;
    private final InventoryGatewayController gateway;
    private final LiveFeedSimulator feed;
    private final AuthService auth;
    private final AuthController authApi;

    private LiteServer() {
        // ---- inventory side: master data, batches, consumption, transfers
        FacilityRepository facilities = MemoryStore.repository(FacilityRepository.class);
        MedicineRepository medicines = MemoryStore.repository(MedicineRepository.class);
        BatchRepository batches = MemoryStore.repository(BatchRepository.class);
        ConsumptionLogRepository logs = MemoryStore.repository(ConsumptionLogRepository.class);
        CaseTrendRepository cases = MemoryStore.repository(CaseTrendRepository.class);
        StockTransferRepository transfers = MemoryStore.repository(StockTransferRepository.class);
        StockTransactionRepository ledger = MemoryStore.repository(StockTransactionRepository.class);
        DataSeeder seeder = new DataSeeder(facilities, medicines, batches, logs, cases);
        seeder.run();
        InventoryService inventoryService =
                new InventoryService(facilities, medicines, batches, logs, cases, transfers, ledger);
        this.feed = new LiveFeedSimulator(facilities, medicines, logs, inventoryService, seeder, true, 12, 5000);
        InventoryController inventory =
                new InventoryController(inventoryService, facilities, medicines, batches, logs, transfers, feed);

        // ---- forecast side: analysis, recommendations, analytics
        LocalInventoryClient client = new LocalInventoryClient(inventory);
        RecommendationRepository recommendations = MemoryStore.repository(RecommendationRepository.class);
        AnalysisEngine engine = new AnalysisEngine();
        RecommendationService recommendationService = new RecommendationService(recommendations, client);
        QueryService queries = new QueryService(client, engine, recommendationService, "Vellore", "Tamil Nadu");
        this.forecast = new ForecastController(queries, recommendationService,
                new AnalyticsService(client, engine, recommendations));
        this.gateway = new InventoryGatewayController(client);
        this.auth = new AuthService(MemoryStore.repository(AppUserRepository.class), client, "Tamil Nadu");
        this.authApi = new AuthController(auth);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8082;
        System.out.println("MediChain Lite: loading demo data...");
        LiteServer app = new LiteServer();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (BindException e) {
            System.out.println("Port " + port + " is already in use. Close the other MediChain window, or run:");
            System.out.println("    java -jar medichain-lite.jar " + (port + 1));
            return;
        }
        server.createContext("/", app::handle);
        server.start();
        // The simulated dispensing feed: the Spring Boot build runs this with @Scheduled.
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dispensing-feed");
            t.setDaemon(true);
            return t;
        }).scheduleWithFixedDelay(() -> {
            try {
                synchronized (MemoryStore.LOCK) {
                    app.feed.tick();
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }, app.feed.getIntervalMs(), app.feed.getIntervalMs(), TimeUnit.MILLISECONDS);
        String url = "http://localhost:" + port;
        System.out.println("MediChain Lite is running. Open " + url);
        System.out.println("Keep this window open. Press Ctrl+C to stop.");
        if (args.length < 2) {
            openBrowser(url);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status = 200;
        byte[] body;
        String type = "application/json; charset=utf-8";
        try {
            if (path.startsWith("/api/")) {
                synchronized (MemoryStore.LOCK) {
                    body = Json.write(api(exchange, path)).getBytes(StandardCharsets.UTF_8);
                }
            } else {
                String file = path.equals("/") ? "/index.html" : path;
                try (InputStream in = file.contains("..") ? null : LiteServer.class.getResourceAsStream("/static" + file)) {
                    if (in == null) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Not found");
                    }
                    body = in.readAllBytes();
                }
                type = file.endsWith(".css") ? "text/css; charset=utf-8"
                        : file.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
            }
        } catch (ResponseStatusException e) {
            status = e.getStatusCode().value();
            body = error(e.getMessage());
        } catch (IllegalArgumentException e) {
            status = 400;
            body = error(e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            status = 500;
            body = error("Server error: " + e);
        }
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** The REST API: same paths and JSON as forecast-service in the Spring Boot build. */
    private Object api(HttpExchange exchange, String path) throws IOException {
        boolean post = "POST".equalsIgnoreCase(exchange.getRequestMethod());
        Map<String, String> q = query(exchange.getRequestURI());
        Map<String, Object> body =
                post ? Json.readObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)) : Map.of();
        String[] parts = path.split("/");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");

        // Same check the Spring interceptor makes in front of every /api call.
        AuthService.Access access = auth.check(exchange.getRequestMethod(), path, authorization, q.get("district"));
        if (!access.allowed()) {
            throw new ResponseStatusException(access.status() == 401 ? org.springframework.http.HttpStatus.UNAUTHORIZED
                    : org.springframework.http.HttpStatus.FORBIDDEN, access.message());
        }

        switch (path) {
            case "/api/auth/regions":
                return authApi.regions();
            case "/api/auth/login":
                return authApi.login(new AuthService.LoginRequest(string(body.get("username")),
                        string(body.get("password")), string(body.get("district"))));
            case "/api/auth/me":
                return authApi.me(authorization);
            case "/api/auth/logout":
                return authApi.logout(authorization);
            case "/api/live":
                return gateway.live(required(q, "district"), q.containsKey("minutes") ? Integer.parseInt(q.get("minutes")) : 30);
            case "/api/meta":
                return forecast.meta();
            case "/api/health":
                return gateway.health();
            case "/api/query":
                return forecast.query(new QueryRequest(string(body.get("query")), string(body.get("district")),
                        body.get("horizonDays") instanceof Number n ? n.intValue() : null));
            case "/api/analysis":
                return forecast.analysis(q.get("district"), longOrNull(q.get("medicineId")), intOrNull(q.get("horizonDays")));
            case "/api/scenario":
                return forecast.scenario(q.get("district"), longOrNull(q.get("medicineId")), intOrNull(q.get("horizonDays")),
                        q.containsKey("casePct") ? Double.parseDouble(q.get("casePct")) : 0,
                        q.containsKey("demandPct") ? Double.parseDouble(q.get("demandPct")) : 0, q.get("disease"));
            case "/api/risk":
                return forecast.risk(q.get("district"), q.get("medicines"), intOrNull(q.get("horizonDays")));
            case "/api/analytics":
                return forecast.analytics(required(q, "district"));
            case "/api/recommendations":
                return forecast.recommendations();
            case "/api/inventory/facilities":
                return post ? gateway.addFacility(body) : gateway.facilities(q.get("district"));
            case "/api/inventory/medicines":
                return gateway.addMedicine(body);
            case "/api/inventory/batches":
                return post ? gateway.receiveBatch(body) : gateway.batches(Long.valueOf(required(q, "facilityId")));
            case "/api/inventory/consumption":
                return gateway.recordConsumption(body);
            case "/api/inventory/transfers":
                return gateway.transfers();
            default:
                if (post && parts.length == 5 && path.startsWith("/api/recommendations/")) {
                    Long id = Long.valueOf(parts[3]);
                    if (parts[4].equals("accept")) {
                        return forecast.accept(id);
                    }
                    if (parts[4].equals("reject")) {
                        return forecast.reject(id);
                    }
                }
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No such endpoint: " + path);
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> map = new HashMap<>();
        if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && !kv[1].isEmpty()) {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        return map;
    }

    private static String required(Map<String, String> q, String key) {
        if (!q.containsKey(key)) {
            throw new IllegalArgumentException("Missing parameter: " + key);
        }
        return q.get(key);
    }

    private static String string(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long longOrNull(String s) {
        return s == null ? null : Long.valueOf(s);
    }

    private static Integer intOrNull(String s) {
        return s == null ? null : Integer.valueOf(s);
    }

    private static byte[] error(String message) {
        return Json.write(Map.of("message", message == null ? "Error" : message)).getBytes(StandardCharsets.UTF_8);
    }

    private static void openBrowser(String url) {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | Error e) {
            // No browser could be started; the address is printed above.
        }
    }
}
