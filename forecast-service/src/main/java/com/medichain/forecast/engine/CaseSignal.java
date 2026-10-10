package com.medichain.forecast.engine;

import java.util.List;

/**
 * Seasonal disease signal: how the last 7 days of reported cases compare with the 3 weeks before.
 * Cases lead medicine demand, so a rising signal lifts the forecast before consumption fully shows it.
 */
public record CaseSignal(String disease, double recentAvg, double baselineAvg, double momentumPct, double uplift) {

    public static final CaseSignal NONE = new CaseSignal(null, 0, 0, 0, 1.0);

    public static CaseSignal of(String disease, List<Integer> dailyCases) {
        int n = dailyCases.size();
        if (n < 28) {
            return new CaseSignal(disease, 0, 0, 0, 1.0);
        }
        double recent = 0;
        for (int i = n - 7; i < n; i++) {
            recent += dailyCases.get(i) / 7.0;
        }
        double baseline = 0;
        for (int i = n - 28; i < n - 7; i++) {
            baseline += dailyCases.get(i) / 21.0;
        }
        if (baseline <= 0) {
            return new CaseSignal(disease, recent, baseline, 0, 1.0);
        }
        double momentum = recent / baseline;
        double uplift = Math.max(0.85, Math.min(1.4, Math.pow(momentum, 0.4)));
        return new CaseSignal(disease, recent, baseline, (momentum - 1) * 100, uplift);
    }
}
