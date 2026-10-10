package com.medichain.forecast.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/** JSON shapes returned by inventory-service. */
public final class InventoryDtos {
    private InventoryDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FacilityDto(Long id, String code, String name, String type, String district,
                              double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MedicineDto(Long id, String code, String name, String category, String unit, String aliases,
                              String linkedDisease, int leadTimeDays, double unitCost) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchDto(Long id, String batchNo, int quantity, LocalDate manufactureDate, LocalDate expiryDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockLine(Long facilityId, Long medicineId, List<BatchDto> batches,
                            List<Integer> dailyDemand, List<Integer> dailyDispensed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseSeries(String disease, List<Integer> dailyCases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(LocalDate asOf, String district, int historyDays, List<FacilityDto> facilities,
                           List<MedicineDto> medicines, List<StockLine> stock, List<CaseSeries> caseTrends) {
    }

    public record TransferRequest(Long fromFacilityId, Long toFacilityId, Long medicineId, int quantity,
                                  Integer minShelfLifeDays, String reference) {
    }
}
