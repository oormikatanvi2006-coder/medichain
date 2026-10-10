package com.medichain.forecast.service;

import com.medichain.forecast.client.InventoryClient;
import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.engine.AnalysisEngine;
import com.medichain.forecast.engine.Results.Analysis;
import com.medichain.forecast.engine.Results.Retrieval;
import com.medichain.forecast.engine.Results.RiskRow;
import com.medichain.forecast.web.ApiModels.Interpreted;
import com.medichain.forecast.web.ApiModels.Meta;
import com.medichain.forecast.web.ApiModels.PipelineStep;
import com.medichain.forecast.web.ApiModels.QueryRequest;
import com.medichain.forecast.web.ApiModels.QueryResponse;
import com.medichain.forecast.web.ApiModels.TransferView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** The end-to-end flow: question, retrieval, analysis, recommendation, explanation. */
@Service
public class QueryService {
    public static final int HISTORY_DAYS = 120;
    public static final int DEFAULT_HORIZON = 10;

    private final InventoryClient inventory;
    private final AnalysisEngine engine;
    private final RecommendationService recommendations;
    private final String defaultDistrict;
    private final String stateName;

    public QueryService(InventoryClient inventory, AnalysisEngine engine, RecommendationService recommendations,
                        @Value("${medichain.default-district}") String defaultDistrict,
                        @Value("${medichain.state-name}") String stateName) {
        this.stateName = stateName;
        this.inventory = inventory;
        this.engine = engine;
        this.recommendations = recommendations;
        this.defaultDistrict = defaultDistrict;
    }

    public Meta meta() {
        Map<String, Integer> bands = new LinkedHashMap<>();
        bands.put("CRITICAL", AnalysisEngine.CRITICAL_DAYS);
        bands.put("HIGH", AnalysisEngine.HIGH_DAYS);
        bands.put("MODERATE", AnalysisEngine.MODERATE_DAYS);
        bands.put("MIN_STOCK_DAYS", AnalysisEngine.MIN_STOCK_DAYS);
        bands.put("MAX_STOCK_DAYS", AnalysisEngine.MAX_STOCK_DAYS);
        return new Meta(stateName, inventory.districts(), defaultDistrict, DEFAULT_HORIZON, inventory.medicines(), bands);
    }

    /** Structured analysis used by the dashboard: one medicine, or all when medicineId is null. */
    public QueryResponse analyse(String district, Long medicineId, Integer horizonDays) {
        List<MedicineDto> all = inventory.medicines();
        List<MedicineDto> chosen = medicineId == null ? List.of()
                : all.stream().filter(m -> m.id().equals(medicineId)).toList();
        String label = chosen.isEmpty() ? "All medicines" : chosen.get(0).name();
        return run(label, district, chosen, all, horizonDays);
    }

    public QueryResponse answer(QueryRequest request) {
        // 1. Understand the question
        List<String> districts = inventory.districts();
        List<MedicineDto> allMedicines = inventory.medicines();
        QueryParser.Parsed parsed = QueryParser.parse(request.query(), districts, allMedicines);
        String district = parsed.district() != null ? parsed.district() : request.district();
        Integer horizon = parsed.horizonDays() != null ? parsed.horizonDays() : request.horizonDays();
        return run(request.query(), district, parsed.medicines(), allMedicines, horizon);
    }

    private QueryResponse run(String queryText, String requestedDistrict, List<MedicineDto> chosen,
                              List<MedicineDto> allMedicines, Integer horizonDays) {
        List<PipelineStep> pipeline = new ArrayList<>();
        String district = requestedDistrict != null && !requestedDistrict.isBlank() ? requestedDistrict : defaultDistrict;
        int horizon = Math.max(1, Math.min(horizonDays != null ? horizonDays : DEFAULT_HORIZON, 90));
        boolean all = chosen.isEmpty();
        List<String> names = (all ? allMedicines : chosen).stream().map(MedicineDto::name).toList();
        Set<Long> ids = chosen.stream().map(MedicineDto::id).collect(Collectors.toSet());
        pipeline.add(new PipelineStep("Query", "District " + district + ", "
                + (all ? "all " + names.size() + " medicines" : String.join(" + ", names)) + ", next " + horizon + " days"));

        // 2. Retrieve from inventory-service over REST
        Snapshot snapshot = inventory.snapshot(district, HISTORY_DAYS);

        // 3. Forecast, risk, surplus matching, procurement
        Analysis analysis = engine.analyse(snapshot, ids, horizon);
        Retrieval r = analysis.retrieval();
        pipeline.add(new PipelineStep("Retrieve", r.facilities() + " facilities, " + r.batches() + " batches with expiry, "
                + r.consumptionRecords() + " daily consumption records, " + r.caseTrendRecords()
                + " disease case records (GET inventory-service /api/snapshot)"));
        long atRisk = analysis.risks().stream().filter(RiskRow::atRiskInHorizon).count();
        pipeline.add(new PipelineStep("Analyse", r.stockLines() + " demand forecasts fitted and stock simulated "
                + AnalysisEngine.SIM_DAYS + " days ahead; " + atRisk + " stock-out risks within " + horizon + " days, "
                + analysis.expiryAlerts().size() + " batch(es) at expiry risk"));

        // 4. Save recommendations
        Set<Long> analysed = new LinkedHashSet<>();
        analysis.risks().forEach(row -> analysed.add(row.medicineId()));
        List<TransferView> transfers =
                recommendations.sync(analysis.district(), analysed, analysis.transfers(), queryText);
        pipeline.add(new PipelineStep("Recommend", transfers.size() + " transfer(s) and " + analysis.reorders().size()
                + " reorder suggestion(s), saved to the recommendation database"));
        pipeline.add(new PipelineStep("Explain", "Each recommendation lists consumption trend, days of stock, "
                + "expiry dates and distance"));

        // 5. Explain
        List<String> findings = new ArrayList<>();
        List<RiskRow> risky = analysis.risks().stream().filter(RiskRow::atRiskInHorizon).toList();
        Set<String> covered = new LinkedHashSet<>();
        for (TransferView t : transfers) {
            findings.add(t.rationale());
            covered.add(t.toCode() + "|" + t.medicineName());
        }
        for (RiskRow row : risky) {
            if (!covered.contains(row.facilityCode() + "|" + row.medicineName())) {
                findings.add(row.facilityName() + " will stock out of " + row.medicineName() + " in "
                        + row.daysToStockout() + " days and no facility within " + (int) AnalysisEngine.MAX_DISTANCE_KM
                        + " km has spare stock: place an emergency order.");
            }
        }
        String scope = all ? "any essential medicine" : String.join(" or ", names);
        String answer;
        if (risky.isEmpty()) {
            RiskRow lowest = analysis.risks().stream().filter(x -> x.daysToStockout() != null)
                    .min((a, b) -> Double.compare(a.daysToStockout(), b.daysToStockout())).orElse(null);
            answer = "No facility in " + analysis.district() + " is forecast to run out of " + scope + " in the next "
                    + horizon + " days." + (lowest == null ? "" : " Lowest cover: " + lowest.facilityName() + " with "
                    + lowest.daysToStockout() + " days of " + lowest.medicineName() + ".");
        } else {
            long facilities = risky.stream().map(RiskRow::facilityCode).distinct().count();
            answer = facilities + (facilities == 1 ? " facility" : " facilities") + " in " + analysis.district()
                    + (facilities == 1 ? " is" : " are") + " at risk of running out of " + scope + " in the next "
                    + horizon + " days (" + risky.size() + " stock line" + (risky.size() == 1 ? "" : "s") + "). "
                    + transfers.size() + " transfer" + (transfers.size() == 1 ? "" : "s")
                    + " from surplus facilities recommended.";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String level : List.of("CRITICAL", "HIGH", "MODERATE", "LOW")) {
            counts.put(level, 0);
        }
        analysis.risks().forEach(row -> counts.merge(row.riskLevel(), 1, Integer::sum));

        return new QueryResponse(queryText, analysis.asOf(),
                new Interpreted(analysis.district(), names, all, horizon), answer, findings, pipeline, r, counts,
                analysis.risks(), transfers, analysis.reorders(), analysis.expiryAlerts(), analysis.caseTrends());
    }
}
