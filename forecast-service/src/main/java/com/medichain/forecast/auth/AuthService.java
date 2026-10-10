package com.medichain.forecast.auth;

import com.medichain.forecast.client.InventoryClient;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sign-in, sessions and access rules.
 *
 * <p>A successful sign-in returns a random token; the browser sends it as {@code Authorization: Bearer ...}
 * on every API call. State administrators may work in any district, district users only in their own.
 * Sessions live in memory and end after 8 hours or at sign-out.
 */
@Service
public class AuthService {
    private static final Duration SESSION_LENGTH = Duration.ofHours(8);
    private static final int HASH_ITERATIONS = 60_000;

    /** What the browser is told about the signed-in user. */
    public record Session(String token, String username, String displayName, String designation, String role,
                          String homeDistrict, String workingDistrict, String stateName, LocalDateTime signedInAt,
                          LocalDateTime previousLoginAt, LocalDateTime expiresAt) {
    }

    public record LoginRequest(String username, String password, String district) {
    }

    /** Result of an access check: status 200 means allowed. */
    public record Access(int status, String message) {
        public boolean allowed() {
            return status == 200;
        }
    }

    private final AppUserRepository users;
    private final InventoryClient inventory;
    private final String stateName;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository users, InventoryClient inventory,
                       @Value("${medichain.state-name}") String stateName) {
        this.users = users;
        this.inventory = inventory;
        this.stateName = stateName;
    }

    /** State and districts for the sign-in page. */
    public Map<String, Object> regions() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stateName", stateName);
        out.put("districts", inventory.districts());
        return out;
    }

    public Session login(LoginRequest request) {
        if (request == null || blank(request.username()) || blank(request.password()) || blank(request.district())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter user ID, password and district.");
        }
        List<String> districts = inventory.districts();
        seedUsers(districts);
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        if (user == null || !MessageDigest.isEqual(hash(request.password(), user.getSalt()).getBytes(),
                user.getPasswordHash().getBytes())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID or password is incorrect.");
        }
        String district = districts.stream().filter(d -> d.equalsIgnoreCase(request.district().trim())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown district."));
        if (user.getDistrict() != null && !user.getDistrict().equalsIgnoreCase(district)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This user ID is registered for " + user.getDistrict() + " district only.");
        }
        LocalDateTime now = LocalDateTime.now();
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Session session = new Session(token, user.getUsername(), user.getDisplayName(), user.getDesignation(),
                user.getRole(), user.getDistrict(), district, stateName, now, user.getLastLoginAt(),
                now.plus(SESSION_LENGTH));
        sessions.put(token, session);
        user.setLastLoginAt(now);
        users.save(user);
        return session;
    }

    public void logout(String authorizationHeader) {
        String token = token(authorizationHeader);
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** The session behind an Authorization header, or null if there is none or it has expired. */
    public Session session(String authorizationHeader) {
        String token = token(authorizationHeader);
        Session s = token == null ? null : sessions.get(token);
        if (s != null && s.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }
        return s;
    }

    /**
     * Decides whether a request may proceed. Used by the Spring interceptor in front of every /api call.
     *
     * @param district the district the request asks for, if any
     */
    public Access check(String method, String path, String authorizationHeader, String district) {
        if ("OPTIONS".equalsIgnoreCase(method) || !path.startsWith("/api/") || path.equals("/api/health")
                || path.equals("/api/auth/login") || path.equals("/api/auth/regions")) {
            return new Access(200, null);
        }
        Session s = session(authorizationHeader);
        if (s == null) {
            return new Access(401, "Session expired or not signed in. Sign in again.");
        }
        if (s.homeDistrict() != null && district != null && !district.isBlank()
                && !s.homeDistrict().equalsIgnoreCase(district)) {
            return new Access(403, "Your user ID is limited to " + s.homeDistrict() + " district.");
        }
        boolean masterData = path.equals("/api/inventory/facilities") || path.equals("/api/inventory/medicines");
        if (masterData && "POST".equalsIgnoreCase(method) && !AppUser.STATE_ADMIN.equals(s.role())) {
            return new Access(403, "Only a state administrator can change master data.");
        }
        return new Access(200, null);
    }

    /**
     * Creates the PoC accounts on first use: one state administrator and one pharmacist per district.
     * Replace this with your own user provisioning before any real use.
     */
    private synchronized void seedUsers(List<String> districts) {
        if (users.findByUsernameIgnoreCase("admin").isEmpty()) {
            users.save(newUser("admin", "Admin@123", "State Administrator", "Drug Logistics Cell, " + stateName,
                    AppUser.STATE_ADMIN, null));
        }
        for (String d : districts) {
            String username = "dp." + d.toLowerCase(Locale.ROOT).replace(" ", "");
            if (users.findByUsernameIgnoreCase(username).isEmpty()) {
                users.save(newUser(username, d + "@123", "District Pharmacist", "District Drug Store, " + d,
                        AppUser.DISTRICT_PHARMACIST, d));
            }
        }
    }

    private AppUser newUser(String username, String password, String displayName, String designation, String role,
                            String district) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltText = Base64.getEncoder().encodeToString(salt);
        return new AppUser(username, hash(password, saltText), saltText, displayName, designation, role, district);
    }

    private static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt),
                    HASH_ITERATIONS, 256);
            return Base64.getEncoder().encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        }
    }

    private static String token(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return authorizationHeader.substring(7).trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
