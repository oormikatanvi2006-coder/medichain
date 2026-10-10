package com.medichain.forecast.web;

import com.medichain.forecast.service.AnalyticsService;
import com.medichain.forecast.service.QueryService;
import com.medichain.forecast.service.RecommendationService;
import com.medichain.forecast.web.ApiModels.Analytics;
import com.medichain.forecast.web.ApiModels.Meta;
import com.medichain.forecast.web.ApiModels.QueryRequest;
import com.medichain.forecast.web.ApiModels.QueryResponse;
import com.medichain.forecast.web.ApiModels.TransferView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ForecastController {
    private final QueryService queries;
    private final RecommendationService recommendations;
    private final AnalyticsService analytics;

    public ForecastController(QueryService queries, RecommendationService recommendations,
                              AnalyticsService analytics) {
        this.queries = queries;
        this.recommendations = recommendations;
        this.analytics = analytics;
    }

    /** Districts and medicines for the UI. */
    @GetMapping("/meta")
    public Meta meta() {
        return queries.meta();
    }

    /** Natural-language question, e.g. "Which facilities will run out of paracetamol and ORS in 10 days?" */
    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) {
        return queries.answer(request);
    }

    /** Same analysis with structured parameters; medicines is a comma-separated list of names or codes. */
    @GetMapping("/risk")
    public QueryResponse risk(@RequestParam(name = "district", required = false) String district,
                              @RequestParam(name = "medicines", required = false) String medicines,
                              @RequestParam(name = "horizonDays", required = false) Integer horizonDays) {
        return queries.answer(new QueryRequest(medicines == null ? "" : medicines.replace(',', ' '), district, horizonDays));
    }

    /** Structured analysis for the dashboard; omit medicineId for all medicines. */
    @GetMapping("/analysis")
    public QueryResponse analysis(@RequestParam(name = "district", required = false) String district,
                                  @RequestParam(name = "medicineId", required = false) Long medicineId,
                                  @RequestParam(name = "horizonDays", required = false) Integer horizonDays) {
        return queries.analyse(district, medicineId, horizonDays);
    }

    @GetMapping("/recommendations")
    public List<TransferView> recommendations() {
        return recommendations.list();
    }

    @PostMapping("/recommendations/{id}/accept")
    public TransferView accept(@PathVariable("id") Long id) {
        return recommendations.accept(id);
    }

    @PostMapping("/recommendations/{id}/reject")
    public TransferView reject(@PathVariable("id") Long id) {
        return recommendations.reject(id);
    }

    @GetMapping("/analytics")
    public Analytics analytics(@RequestParam("district") String district) {
        return analytics.analytics(district);
    }
}
