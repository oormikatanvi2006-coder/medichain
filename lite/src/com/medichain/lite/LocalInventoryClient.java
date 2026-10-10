package com.medichain.lite;

import com.medichain.forecast.client.InventoryClient;
import com.medichain.forecast.client.InventoryDtos.BatchDto;
import com.medichain.forecast.client.InventoryDtos.CaseSeries;
import com.medichain.forecast.client.InventoryDtos.FacilityDto;
import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.client.InventoryDtos.StockLine;
import com.medichain.forecast.client.InventoryDtos.TransferRequest;
import com.medichain.inventory.dto.Dtos;
import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import com.medichain.inventory.web.InventoryController;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * In the Spring Boot build the forecast service reaches the inventory service over HTTP. Here both run in
 * one process, so the same calls go straight to the inventory controller.
 */
final class LocalInventoryClient extends InventoryClient {
    private final InventoryController inventory;

    LocalInventoryClient(InventoryController inventory) {
        super(new RestClient.Builder() {
            public RestClient.Builder baseUrl(String url) {
                return this;
            }

            public RestClient build() {
                return null;
            }
        }, "in-process");
        this.inventory = inventory;
    }

    @Override
    public Snapshot snapshot(String district, int days) {
        Dtos.Snapshot s = inventory.snapshot(district, days);
        return new Snapshot(s.asOf(), s.district(), s.historyDays(),
                s.facilities().stream().map(f -> new FacilityDto(f.getId(), f.getCode(), f.getName(), f.getType(),
                        f.getDistrict(), f.getLatitude(), f.getLongitude())).toList(),
                medicines(),
                s.stock().stream().map(l -> new StockLine(l.facilityId(), l.medicineId(),
                        l.batches().stream().map(b -> new BatchDto(b.id(), b.batchNo(), b.quantity(),
                                b.manufactureDate(), b.expiryDate())).toList(),
                        l.dailyDemand(), l.dailyDispensed())).toList(),
                s.caseTrends().stream().map(c -> new CaseSeries(c.disease(), c.dailyCases())).toList());
    }

    @Override
    public List<String> districts() {
        return inventory.districts();
    }

    @Override
    public List<MedicineDto> medicines() {
        return inventory.medicines().stream().map(m -> new MedicineDto(m.getId(), m.getCode(), m.getName(),
                m.getCategory(), m.getUnit(), m.getAliases(), m.getLinkedDisease(), m.getLeadTimeDays(),
                m.getUnitCost())).toList();
    }

    @Override
    public List<Map<String, Object>> getList(String path, Object... vars) {
        List<?> rows;
        if (path.startsWith("/api/facilities")) {
            rows = inventory.facilities(vars.length == 0 ? null : String.valueOf(vars[0]));
        } else if (path.startsWith("/api/batches")) {
            rows = inventory.batches((Long) vars[0], null);
        } else if (path.startsWith("/api/transfers")) {
            rows = inventory.transfers();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown inventory path " + path);
        }
        return rows.stream().map(Json::toMap).toList();
    }

    @Override
    public Map<String, Object> getMap(String path, Object... vars) {
        if (path.startsWith("/api/live")) {
            return Json.toMap(inventory.live(String.valueOf(vars[0]), ((Number) vars[1]).intValue()));
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown inventory path " + path);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> b) {
        switch (path) {
            case "/api/facilities":
                return Json.toMap(inventory.addFacility(new Facility(text(b, "code"), text(b, "name"), text(b, "type"),
                        text(b, "district"), number(b, "latitude").doubleValue(), number(b, "longitude").doubleValue())));
            case "/api/medicines":
                return Json.toMap(inventory.addMedicine(new Medicine(text(b, "code"), text(b, "name"),
                        text(b, "category"), text(b, "unit"), text(b, "aliases"), text(b, "linkedDisease"),
                        number(b, "leadTimeDays").intValue(), number(b, "unitCost").doubleValue(), 0)));
            case "/api/batches":
                return Json.toMap(inventory.receiveBatch(new Dtos.BatchRequest(number(b, "facilityId").longValue(),
                        number(b, "medicineId").longValue(), text(b, "batchNo"), number(b, "quantity").intValue(),
                        date(b, "manufactureDate"), date(b, "expiryDate"))));
            case "/api/consumption": {
                long facilityId = number(b, "facilityId").longValue();
                long medicineId = number(b, "medicineId").longValue();
                int dispensed = number(b, "dispensed").intValue();
                // No database transaction to roll back here, so check stock before anything is changed.
                int usable = inventory.batches(facilityId, medicineId).stream()
                        .filter(x -> x.getExpiryDate().isAfter(LocalDate.now())).mapToInt(Batch::getQuantity).sum();
                if (dispensed > usable) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot dispense more than the usable stock on hand (" + usable + ")");
                }
                return Json.toMap(inventory.recordConsumption(new Dtos.ConsumptionRequest(facilityId, medicineId, null,
                        number(b, "demanded").intValue(), dispensed)));
            }
            default:
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown inventory path " + path);
        }
    }

    @Override
    public Map<String, Object> transfer(TransferRequest r) {
        return Json.toMap(inventory.transfer(new Dtos.TransferRequest(r.fromFacilityId(), r.toFacilityId(),
                r.medicineId(), r.quantity(), r.minShelfLifeDays(), r.reference())));
    }

    private static String text(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static Number number(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (!(v instanceof Number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + key + "' must be a number");
        }
        return (Number) v;
    }

    private static LocalDate date(Map<String, Object> body, String key) {
        Object v = body.get(key);
        try {
            return v == null || v.toString().isBlank() ? null : LocalDate.parse(v.toString());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + key + "' must be a date (yyyy-mm-dd)");
        }
    }
}
