package com.medichain.forecast.service;

import com.medichain.forecast.client.InventoryClient;
import com.medichain.forecast.client.InventoryDtos.BatchDto;
import com.medichain.forecast.client.InventoryDtos.FacilityDto;
import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.client.InventoryDtos.StockLine;
import com.medichain.forecast.engine.AnalysisEngine;
import com.medichain.forecast.engine.Results.Analysis;
import com.medichain.forecast.engine.Results.ExpiryAlert;
import com.medichain.forecast.engine.Results.RiskRow;
import com.medichain.forecast.model.Recommendation;
import com.medichain.forecast.repo.RecommendationRepository;
import com.medichain.forecast.web.ApiModels.Analytics;
import com.medichain.forecast.web.ApiModels.DayStats;
import com.medichain.forecast.web.ApiModels.FacilityStats;
import com.medichain.forecast.web.ApiModels.WeekStats;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Supply analytics: stock-out rate, fill rate, wastage and what happened to recommendations. */
@Service
public class AnalyticsService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final InventoryClient inventory;
    private final AnalysisEngine engine;
    private final RecommendationRepository recommendations;

    public AnalyticsService(InventoryClient inventory, AnalysisEngine engine,
                            RecommendationRepository recommendations) {
        this.inventory = inventory;
        this.engine = engine;
        this.recommendations = recommendations;
    }

    public Analytics analytics(String district) {
        Snapshot snap = inventory.snapshot(district, QueryService.HISTORY_DAYS);
        Analysis analysis = engine.analyse(snap, Set.of(), 14);
        int days = snap.historyDays();

        Map<Long, MedicineDto> medicines = new HashMap<>();
        snap.medicines().forEach(m -> medicines.put(m.id(), m));

        // [demanded, dispensed, stock-out days, line-days] for the last 30 and 90 days
        double[] w30 = new double[4];
        double[] w90 = new double[4];
        int weeks = 12;
        double[][] weekly = new double[weeks][4];
        int dayCount = Math.min(30, days);
        double[][] daily = new double[dayCount][4];
        Map<Long, double[]> perFacility = new HashMap<>();
        int expiredUnits = 0;
        double expiredValue = 0;

        for (StockLine line : snap.stock()) {
            double[] fac = perFacility.computeIfAbsent(line.facilityId(), k -> new double[4]);
            for (int i = 0; i < line.dailyDemand().size(); i++) {
                int age = days - i;                       // 1 = yesterday
                int demanded = line.dailyDemand().get(i);
                int dispensed = line.dailyDispensed().get(i);
                boolean stockout = demanded > 0 && dispensed < demanded;
                if (age <= 30) {
                    add(w30, demanded, dispensed, stockout);
                    add(fac, demanded, dispensed, stockout);
                }
                if (age <= 90) {
                    add(w90, demanded, dispensed, stockout);
                }
                if (age <= dayCount) {
                    add(daily[dayCount - age], demanded, dispensed, stockout);
                }
                int week = (age - 1) / 7;
                if (week < weeks) {
                    add(weekly[weeks - 1 - week], demanded, dispensed, stockout);
                }
            }
            MedicineDto m = medicines.get(line.medicineId());
            for (BatchDto b : line.batches()) {
                if (!b.expiryDate().isAfter(snap.asOf())) {
                    expiredUnits += b.quantity();
                    expiredValue += b.quantity() * (m == null ? 0 : m.unitCost());
                }
            }
        }

        int riskUnits = 0;
        double riskValue = 0;
        Map<Long, Integer> riskUnitsByFacility = new HashMap<>();
        for (ExpiryAlert a : analysis.expiryAlerts()) {
            riskUnits += a.unitsLikelyToExpire();
            riskValue += a.valueAtRisk();
            riskUnitsByFacility.merge(a.facilityId(), a.unitsLikelyToExpire(), Integer::sum);
        }

        Map<String, Integer> riskCounts = new LinkedHashMap<>();
        for (String level : List.of("CRITICAL", "HIGH", "MODERATE", "LOW")) {
            riskCounts.put(level, 0);
        }
        Map<Long, Integer> criticalByFacility = new HashMap<>();
        for (RiskRow row : analysis.risks()) {
            riskCounts.merge(row.riskLevel(), 1, Integer::sum);
            if ("CRITICAL".equals(row.riskLevel()) || "HIGH".equals(row.riskLevel())) {
                criticalByFacility.merge(row.facilityId(), 1, Integer::sum);
            }
        }

        Map<String, Integer> recCounts = new LinkedHashMap<>();
        for (String status : List.of(Recommendation.PROPOSED, Recommendation.EXECUTED, Recommendation.REJECTED,
                Recommendation.SUPERSEDED)) {
            recCounts.put(status, 0);
        }
        int moved = 0;
        int executed = 0;
        int expirySaved = 0;
        double expirySavedValue = 0;
        double coverAdded = 0;
        for (Recommendation r : recommendations.findAll()) {
            if (r.getDistrict() == null || !r.getDistrict().equalsIgnoreCase(snap.district())) {
                continue;
            }
            recCounts.merge(r.getStatus(), 1, Integer::sum);
            if (Recommendation.EXECUTED.equals(r.getStatus())) {
                moved += r.getQuantity();
                executed++;
                expirySaved += r.getExpirySavedUnits();
                expirySavedValue += r.getExpirySavedValue();
                coverAdded += r.getCoverDaysGained();
            }
        }
        int decided = recCounts.get(Recommendation.EXECUTED) + recCounts.get(Recommendation.REJECTED);
        double acceptance = decided == 0 ? 0 : 100.0 * recCounts.get(Recommendation.EXECUTED) / decided;

        List<FacilityStats> facilityStats = new ArrayList<>();
        for (FacilityDto f : snap.facilities()) {
            double[] s = perFacility.getOrDefault(f.id(), new double[4]);
            facilityStats.add(new FacilityStats(f.code(), f.name(), pct(s[1], s[0]), (int) s[2],
                    criticalByFacility.getOrDefault(f.id(), 0), riskUnitsByFacility.getOrDefault(f.id(), 0)));
        }

        List<WeekStats> weekStats = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            int firstDayAge = (weeks - w) * 7;
            weekStats.add(new WeekStats(snap.asOf().minusDays(firstDayAge).format(DAY),
                    pct(weekly[w][1], weekly[w][0]), pct(weekly[w][2], weekly[w][3])));
        }

        List<DayStats> dayStats = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) {
            dayStats.add(new DayStats(snap.asOf().minusDays(dayCount - d), pct(daily[d][1], daily[d][0]),
                    (int) daily[d][2], (int) daily[d][1], (int) (daily[d][0] - daily[d][1])));
        }

        return new Analytics(snap.district(), snap.asOf(), pct(w30[1], w30[0]), pct(w90[1], w90[0]),
                pct(w30[2], w30[3]), pct(w90[2], w90[3]), (int) w30[2], (int) (w30[0] - w30[1]), expiredUnits,
                Math.round(expiredValue), riskUnits, Math.round(riskValue), riskCounts, recCounts, moved,
                Math.round(acceptance * 10) / 10.0, facilityStats, weekStats, dayStats, executed, expirySaved,
                expirySavedValue, Math.round(coverAdded * 10) / 10.0);
    }

    private static void add(double[] acc, int demanded, int dispensed, boolean stockout) {
        acc[0] += demanded;
        acc[1] += dispensed;
        acc[2] += stockout ? 1 : 0;
        acc[3] += 1;
    }

    private static double pct(double part, double whole) {
        return whole <= 0 ? 0 : Math.round(1000.0 * part / whole) / 10.0;
    }
}
