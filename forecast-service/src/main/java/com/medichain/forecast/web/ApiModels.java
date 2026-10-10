package com.medichain.forecast.web;

import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.engine.Results.ExpiryAlert;
import com.medichain.forecast.engine.Results.Reorder;
import com.medichain.forecast.engine.Results.Retrieval;
import com.medichain.forecast.engine.Results.RiskRow;
import com.medichain.forecast.model.Recommendation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the forecast REST API. */
public final class ApiModels {
    private ApiModels() {
    }

    public record QueryRequest(String query, String district, Integer horizonDays) {
    }

    public record Interpreted(String district, List<String> medicines, boolean allMedicines, int horizonDays) {
    }

    public record PipelineStep(String step, String detail) {
    }

    public record TransferView(Long id, String status, String district, Long medicineId, String medicineName,
                               String unit, Long fromFacilityId, String fromCode, String fromName,
                               Long toFacilityId, String toCode, String toName, int quantity,
                               int fromStockBefore, int fromStockAfter, int toStockBefore, int toStockAfter,
                               double distanceKm,
                               double receiverDaysBefore, double receiverDaysAfter, double donorDaysBefore,
                               double donorDaysAfter, String urgency, String rationale, List<String> keyFactors,
                               List<String> batches, LocalDateTime createdAt, LocalDateTime decidedAt) {

        public static TransferView of(Recommendation r) {
            return new TransferView(r.getId(), r.getStatus(), r.getDistrict(), r.getMedicineId(),
                    r.getMedicineName(), r.getUnit(), r.getFromFacilityId(), r.getFromCode(), r.getFromName(),
                    r.getToFacilityId(), r.getToCode(), r.getToName(), r.getQuantity(), r.getFromStockBefore(),
                    r.getFromStockAfter(), r.getToStockBefore(), r.getToStockAfter(), r.getDistanceKm(),
                    r.getReceiverDaysBefore(), r.getReceiverDaysAfter(), r.getDonorDaysBefore(),
                    r.getDonorDaysAfter(), r.getUrgency(), r.getRationale(), lines(r.getKeyFactors()),
                    lines(r.getBatches()), r.getCreatedAt(), r.getDecidedAt());
        }

        private static List<String> lines(String text) {
            return text == null || text.isBlank() ? List.of() : Arrays.asList(text.split("\n"));
        }
    }

    public record QueryResponse(String query, LocalDate asOf, Interpreted interpreted, String answer,
                                List<String> findings, List<PipelineStep> pipeline, Retrieval retrieval,
                                Map<String, Integer> riskCounts, List<RiskRow> risks, List<TransferView> transfers,
                                List<Reorder> procurement, List<ExpiryAlert> expiryAlerts,
                                Map<String, List<Integer>> caseTrends) {
    }

    public record Meta(String stateName, List<String> districts, String defaultDistrict, int defaultHorizonDays,
                       List<MedicineDto> medicines, Map<String, Integer> riskBands) {
    }

    public record FacilityStats(String code, String name, double fillRatePct, int stockoutDays, int criticalItems,
                                int unitsAtExpiryRisk) {
    }

    public record DayStats(LocalDate date, double fillRatePct, int linesShort, int issuedUnits, int unmetUnits) {
    }

    public record WeekStats(String weekStarting, double fillRatePct, double stockoutRatePct) {
    }

    public record Analytics(String district, LocalDate asOf, double fillRate30Pct, double fillRate90Pct,
                            double stockoutRate30Pct, double stockoutRate90Pct, int stockoutDays30,
                            int unmetUnits30, int expiredUnitsOnHand, double expiredValue, int unitsAtExpiryRisk,
                            double valueAtExpiryRisk, Map<String, Integer> riskCounts,
                            Map<String, Integer> recommendationCounts, int unitsRedistributed,
                            double acceptanceRatePct, List<FacilityStats> facilities, List<WeekStats> weeks,
                            List<DayStats> days) {
    }
}
