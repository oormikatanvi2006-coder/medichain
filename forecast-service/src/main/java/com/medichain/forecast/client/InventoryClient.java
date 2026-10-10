package com.medichain.forecast.client;

import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.client.InventoryDtos.TransferRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** REST client for inventory-service: the inter-service communication of the platform. */
@Component
public class InventoryClient {
    private final RestClient http;

    public InventoryClient(RestClient.Builder builder,
                           @Value("${medichain.inventory.base-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public Snapshot snapshot(String district, int days) {
        return http.get()
                .uri("/api/snapshot?district={district}&days={days}", district, days)
                .retrieve()
                .body(Snapshot.class);
    }

    public List<String> districts() {
        return http.get().uri("/api/districts").retrieve()
                .body(new ParameterizedTypeReference<List<String>>() { });
    }

    public List<MedicineDto> medicines() {
        return http.get().uri("/api/medicines").retrieve()
                .body(new ParameterizedTypeReference<List<MedicineDto>>() { });
    }

    /** Generic pass-through used by the master-data and inventory screens. */
    public List<Map<String, Object>> getList(String path, Object... uriVariables) {
        return http.get().uri(path, uriVariables).retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
    }

    public Map<String, Object> getMap(String path, Object... uriVariables) {
        return http.get().uri(path, uriVariables).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        return http.post().uri(path).body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    public Map<String, Object> transfer(TransferRequest request) {
        return http.post().uri("/api/transfers").body(request).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }
}
