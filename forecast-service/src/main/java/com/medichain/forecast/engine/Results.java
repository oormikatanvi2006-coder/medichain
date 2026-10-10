package com.medichain.forecast.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Output of the analysis engine. */
public final class Results {
    private Results() {
    }

    /** Stock-out risk of one medicine at one facility. daysToStockout is null when stock lasts 180+ days. */
    public record RiskRow(Long facilityId, String facilityCode, String facilityName, String facilityType,
                          Long medicineId, String medicineName, String unit, int currentStock,
                          double avgMonthlyConsumption, Double monthsOfStock, String stockStatus,
                          double forecastDailyDemand, double recentDailyDemand, double trendPct,
                          Double daysToStockout, LocalDate stockoutDate, String riskLevel, int riskScore,
                          boolean atRiskInHorizon, double stockoutProbabilityPct, String linkedDisease,
                          double caseMomentumPct, double seasonalUpliftPct, double backtestErrorPct,
                          int unitsAtExpiryRisk, String suggestedAction, List<String> keyFactors,
                          List<BatchView> batches, List<Integer> history, List<Integer> forecast) {
    }

    /** A batch as shown in the UI; status is EXPIRED, EXPIRING_SOON (90 days or less) or USABLE. */
    public record BatchView(String batchNo, int quantity, LocalDate manufactureDate, LocalDate expiryDate,
                            int daysToExpiry, String status) {
    }

    public record ExpiryAlert(Long facilityId, String facilityCode, String facilityName, Long medicineId,
                              String medicineName, String unit, String batchNo, LocalDate expiryDate,
                              int daysToExpiry, int batchQuantity, int unitsLikelyToExpire, double valueAtRisk,
                              String action) {
    }

    public record Transfer(Long medicineId, String medicineName, String unit, Long fromFacilityId, String fromCode,
                           String fromName, Long toFacilityId, String toCode, String toName, int quantity,
                           int fromStockBefore, int fromStockAfter, int toStockBefore, int toStockAfter,
                           double distanceKm, double receiverDaysBefore, double receiverDaysAfter,
                           double donorDaysBefore, double donorDaysAfter, String urgency, String rationale,
                           List<String> keyFactors, List<String> batches, int expirySavedUnits,
                           double expirySavedValue, double coverDaysGained) {
    }

    public record Reorder(Long facilityId, String facilityCode, String facilityName, Long medicineId,
                          String medicineName, String unit, int quantity, LocalDate orderBy,
                          LocalDate expectedDelivery, int leadTimeDays, String status, double coverDays,
                          int reorderPoint, int safetyStock, int stockPosition, double estimatedCost,
                          String rationale, List<String> keyFactors) {
    }

    public record Retrieval(int facilities, int medicines, int stockLines, int batches, int consumptionRecords,
                            int caseTrendRecords, int historyDays) {
    }

    public record Analysis(LocalDate asOf, String district, int horizonDays, Retrieval retrieval,
                           List<RiskRow> risks, List<ExpiryAlert> expiryAlerts, List<Transfer> transfers,
                           List<Reorder> reorders, Map<String, List<Integer>> caseTrends) {
    }
}
