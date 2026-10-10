package com.medichain.forecast.web;

import com.medichain.forecast.client.InventoryClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/**
 * Lets the web UI reach inventory-service through one origin: every call here is forwarded over REST.
 * Master data, batches and consumption stay owned by inventory-service.
 */
@RestController
@RequestMapping("/api")
public class InventoryGatewayController {
    private final InventoryClient inventory;

    public InventoryGatewayController(InventoryClient inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("forecastService", "UP");
        try {
            inventory.districts();
            status.put("inventoryService", "UP");
        } catch (RestClientException e) {
            status.put("inventoryService", "DOWN");
        }
        return status;
    }

    /** Today's stock movement from the inventory ledger (issues per minute, latest transactions). */
    @GetMapping("/live")
    public Map<String, Object> live(@RequestParam("district") String district,
                                    @RequestParam(name = "minutes", defaultValue = "30") int minutes) {
        return inventory.getMap("/api/live?district={district}&minutes={minutes}", district, minutes);
    }

    @GetMapping("/inventory/facilities")
    public List<Map<String, Object>> facilities(@RequestParam(name = "district", required = false) String district) {
        return district == null || district.isBlank()
                ? inventory.getList("/api/facilities")
                : inventory.getList("/api/facilities?district={district}", district);
    }

    @PostMapping("/inventory/facilities")
    public Map<String, Object> addFacility(@RequestBody Map<String, Object> body) {
        return inventory.post("/api/facilities", body);
    }

    @PostMapping("/inventory/medicines")
    public Map<String, Object> addMedicine(@RequestBody Map<String, Object> body) {
        return inventory.post("/api/medicines", body);
    }

    @GetMapping("/inventory/batches")
    public List<Map<String, Object>> batches(@RequestParam("facilityId") Long facilityId) {
        return inventory.getList("/api/batches?facilityId={facilityId}", facilityId);
    }

    @PostMapping("/inventory/batches")
    public Map<String, Object> receiveBatch(@RequestBody Map<String, Object> body) {
        return inventory.post("/api/batches", body);
    }

    @PostMapping("/inventory/consumption")
    public Map<String, Object> recordConsumption(@RequestBody Map<String, Object> body) {
        return inventory.post("/api/consumption", body);
    }

    @GetMapping("/inventory/transfers")
    public List<Map<String, Object>> transfers() {
        return inventory.getList("/api/transfers");
    }
}
