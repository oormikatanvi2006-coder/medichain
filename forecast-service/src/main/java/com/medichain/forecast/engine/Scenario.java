package com.medichain.forecast.engine;

/**
 * A what-if adjustment applied on top of the real data before the analysis runs.
 *
 * @param caseFactor   multiplier on the last 7 days of reported disease cases (1.5 = cases up 50%)
 * @param demandFactor multiplier on forecast demand for every stock line (1.2 = demand up 20%)
 * @param disease      disease the case change applies to, or null for all diseases
 */
public record Scenario(double caseFactor, double demandFactor, String disease) {
    public static final Scenario NONE = new Scenario(1.0, 1.0, null);

    boolean appliesTo(String candidate) {
        return disease == null || disease.isBlank() || disease.equalsIgnoreCase(candidate);
    }
}
