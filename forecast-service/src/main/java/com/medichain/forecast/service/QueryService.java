package com.medichain.forecast.service;

import com.medichain.forecast.client.InventoryClient;
import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.engine.AnalysisEngine;
import com.medichain.forecast.engine.Results.Analysis;
import com.medichain.forecast.engine.Results.Retrieval;
import com.medichain.forecast.engine.Results.RiskRow;
import com.medichain.forecast.engine.Scenario;
import com.medichain.forecast.web.ApiModels.ScenarioChange;
import com.medichain.forecast.web.ApiModels.ScenarioResult;
import com.medichain.forecast.web.ApiModels.ScenarioSummary;
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

    /**
     * What-if: runs the analysis on the current data and again with disease cases and demand changed, and
     * reports what moves. Nothing is saved; transfer advice from a scenario is for planning only.
     */
    public ScenarioResult scenario(String requestedDistrict, Long medicineId, Integer horizonDays, double casePct,
                                   double demandPct, String disease) {
        String district = requestedDistrict != null && !requestedDistrict.isBlank() ? requestedDistrict : defaultDistrict;
        int horizon = Math.max(1, Math.min(horizonDays != null ? horizonDays : DEFAULT_HORIZON, 90));
        double cases = Math.max(-90, Math.min(casePct, 300));
        double demand = Math.max(-50, Math.min(demandPct, 100));
        Set<Long> ids = medicineId == null ? Set.of() : Set.of(medicineId);
        Snapshot snapshot = inventory.snapshot(district, HISTORY_DAYS);
        Analysis base = engine.analyse(snapshot, ids, horizon);
        Analysis changed = engine.analyse(snapshot, ids, horizon,
                new Scenario(1 + cases / 100.0, 1 + demand / 100.0, disease));

        Map<String, RiskRow> baseRows = new LinkedHashMap<>();
        base.risks().forEach(r -> baseRows.put(r.facilityId() + ":" + r.medicineId(), r));
        List<ScenarioChange> changes = new ArrayList<>();
        for (RiskRow s : changed.risks()) {
            RiskRow b = baseRows.get(s.facilityId() + ":" + s.medicineId());
            if (b == null) {
                continue;
            }
            boolean riskMoved = !b.riskLevel().equals(s.riskLevel());
            boolean daysMoved = b.daysToStockout() != null && s.daysToStockout() != null
                    && Math.abs(b.daysToStockout() - s.daysToStockout()) >= 0.5
                    && !("LOW".equals(b.riskLevel()) && "LOW".equals(s.riskLevel()));
            if (riskMoved || daysMoved) {
                changes.add(new ScenarioChange(s.facilityName(), s.medicineName(), s.unit(), s.currentStock(),
                        b.forecastDailyDemand(), s.forecastDailyDemand(), b.daysToStockout(), s.daysToStockout(),
                        b.riskLevel(), s.riskLevel(), s.suggestedAction()));
            }
        }
        return new ScenarioResult(changed.district(), horizon, cases, demand,
                disease == null || disease.isBlank() ? null : disease.toUpperCase(), summary(base), summary(changed),
                changes.size() > 40 ? changes.subList(0, 40) : changes, changed.transfers());
    }

    private static ScenarioSummary summary(Analysis a) {
        int due = 0;
        int critical = 0;
        int high = 0;
        long forecast = 0;
        Set<String> served = new LinkedHashSet<>();
        a.transfers().forEach(t -> served.add(t.toFacilityId() + ":" + t.medicineId()));
        int noDonor = 0;
        for (RiskRow r : a.risks()) {
            if (r.atRiskInHorizon()) {
                due++;
                if (!served.contains(r.facilityId() + ":" + r.medicineId())) {
                    noDonor++;
                }
            }
            if ("CRITICAL".equals(r.riskLevel())) {
                critical++;
            } else if ("HIGH".equals(r.riskLevel())) {
                high++;
            }
            for (int units : r.forecast()) {
                forecast += units;
            }
        }
        int transferUnits = a.transfers().stream().mapToInt(t -> t.quantity()).sum();
        int expiryUnits = a.expiryAlerts().stream().mapToInt(e -> e.unitsLikelyToExpire()).sum();
        return new ScenarioSummary(due, critical, high, a.transfers().size(), transferUnits, noDonor,
                a.reorders().size(), expiryUnits, forecast);
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
        pipeline.add(new PipelineStep("Query", district + " | "
                + (all ? "all " + names.size() + " drugs" : String.join(", ", names)) + " | " + horizon + " days"));

        // 2. Retrieve from inventory-service over REST
        Snapshot snapshot = inventory.snapshot(district, HISTORY_DAYS);

        // 3. Forecast, risk, surplus matching, procurement
        Analysis analysis = engine.analyse(snapshot, ids, horizon);
        Retrieval r = analysis.retrieval();
        pipeline.add(new PipelineStep("Retrieve", r.facilities() + " facilities, " + r.batches() + " batches, "
                + r.consumptionRecords() + " consumption rows, " + r.caseTrendRecords() + " case rows"));
        long atRisk = analysis.risks().stream().filter(RiskRow::atRiskInHorizon).count();
        pipeline.add(new PipelineStep("Analyse", r.stockLines() + " forecasts; " + atRisk + " stock-outs due; "
                + analysis.expiryAlerts().size() + " batches at expiry risk"));

        // 4. Save recommendations
        Set<Long> analysed = new LinkedHashSet<>();
        analysis.risks().forEach(row -> analysed.add(row.medicineId()));
        List<TransferView> transfers =
                recommendations.sync(analysis.district(), analysed, analysis.transfers(), queryText);
        pipeline.add(new PipelineStep("Recommend", transfers.size() + " transfers, " + analysis.reorders().size()
                + " indents"));

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
                findings.add(row.facilityName() + ": " + row.medicineName() + " out in " + row.daysToStockout()
                        + " days. No donor within " + (int) AnalysisEngine.MAX_DISTANCE_KM + " km. Emergency indent.");
            }
        }
        String answer;
        if (risky.isEmpty()) {
            answer = analysis.district() + ", next " + horizon + " days: no stock-out due.";
        } else {
            long facilities = risky.stream().map(RiskRow::facilityCode).distinct().count();
            answer = analysis.district() + ", next " + horizon + " days: " + risky.size() + " stock lines at "
                    + facilities + " facilities due to stock out. " + transfers.size() + " transfers advised.";
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
