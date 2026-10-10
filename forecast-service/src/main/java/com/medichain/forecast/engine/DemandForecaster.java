package com.medichain.forecast.engine;

import java.time.LocalDate;

/**
 * Demand forecasting model.
 *
 * <ol>
 *   <li>Day-of-week seasonality is learned from history and removed (clinics are quiet on Sundays).</li>
 *   <li>A damped-trend Holt exponential smoothing model is fitted; the smoothing parameters are chosen
 *       per series by grid search to minimise one-step-ahead squared error.</li>
 *   <li>The forecast is multiplied by an outbreak uplift taken from district disease surveillance.</li>
 *   <li>Accuracy is measured by back-testing: the last 14 days are hidden, forecast and compared.</li>
 * </ol>
 */
public final class DemandForecaster {
    /** Trend damping: keeps long-range forecasts from running away. */
    public static final double PHI = 0.9;
    private static final double[] ALPHAS = {0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8};
    private static final double[] BETAS = {0.01, 0.05, 0.1, 0.2, 0.3};
    private static final int BACKTEST_DAYS = 14;

    private DemandForecaster() {
    }

    /**
     * @param daily     forecast units per day; index 0 is the as-of date
     * @param sigma     standard deviation of one-day forecast errors
     * @param wape      back-test error (sum of absolute errors / sum of actuals), or -1 if history is too short
     * @param trendPct  change of the last 14 days against the 14 days before, in percent
     * @param recentAvg average daily demand over the last 14 days
     */
    public record Forecast(double[] daily, double sigma, double wape, double alpha, double beta,
                           double trendPct, double recentAvg) {
    }

    private record Fit(double alpha, double beta, double level, double trend, double sse, int scored) {
    }

    /** @param history daily demand, oldest first, ending the day before {@code asOf} */
    public static Forecast forecast(int[] history, LocalDate asOf, int horizon, double uplift) {
        int n = history.length;
        double[] dow = dayOfWeekFactors(history, asOf);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = history[i] / dow[dayIndex(asOf, i - n)];
        }

        Fit fit = fit(x, n);
        double[] daily = new double[horizon];
        double damp = 0;
        double p = 1;
        for (int h = 0; h < horizon; h++) {
            p *= PHI;
            damp += p;
            double base = Math.max(0, fit.level() + damp * fit.trend());
            daily[h] = base * dow[dayIndex(asOf, h)] * uplift;
        }
        double sigma = Math.sqrt(fit.sse() / Math.max(1, fit.scored()));

        double wape = -1;
        if (n >= 56) {
            int cut = n - BACKTEST_DAYS;
            Fit past = fit(x, cut);
            double absErr = 0;
            double actual = 0;
            damp = 0;
            p = 1;
            for (int h = 0; h < BACKTEST_DAYS; h++) {
                p *= PHI;
                damp += p;
                double f = Math.max(0, past.level() + damp * past.trend()) * dow[dayIndex(asOf, cut + h - n)];
                absErr += Math.abs(f - history[cut + h]);
                actual += history[cut + h];
            }
            wape = actual > 0 ? absErr / actual : 0;
        }

        double recent = mean(history, n - 14, n);
        double before = mean(history, n - 28, n - 14);
        double trendPct = before > 0 ? (recent - before) / before * 100 : 0;
        return new Forecast(daily, sigma, wape, fit.alpha(), fit.beta(), trendPct, recent);
    }

    private static Fit fit(double[] x, int len) {
        if (len < 21) {
            double m = 0;
            for (int i = 0; i < len; i++) {
                m += x[i];
            }
            m = len > 0 ? m / len : 0;
            double sse = 0;
            for (int i = 0; i < len; i++) {
                sse += (x[i] - m) * (x[i] - m);
            }
            return new Fit(0, 0, m, 0, sse, len);
        }
        double first = 0;
        double second = 0;
        for (int i = 0; i < 7; i++) {
            first += x[i] / 7;
            second += x[i + 7] / 7;
        }
        Fit best = null;
        for (double alpha : ALPHAS) {
            for (double beta : BETAS) {
                double level = first;
                double trend = (second - first) / 7;
                double sse = 0;
                int scored = 0;
                for (int t = 7; t < len; t++) {
                    double predicted = level + PHI * trend;
                    if (t >= 14) {
                        sse += (x[t] - predicted) * (x[t] - predicted);
                        scored++;
                    }
                    double newLevel = alpha * x[t] + (1 - alpha) * predicted;
                    trend = beta * (newLevel - level) + (1 - beta) * PHI * trend;
                    level = newLevel;
                }
                if (best == null || sse < best.sse()) {
                    best = new Fit(alpha, beta, level, trend, sse, scored);
                }
            }
        }
        return best;
    }

    /** Average demand on each weekday divided by the overall average (Monday = index 0). */
    private static double[] dayOfWeekFactors(int[] history, LocalDate asOf) {
        int n = history.length;
        double[] sum = new double[7];
        int[] count = new int[7];
        double total = 0;
        for (int i = 0; i < n; i++) {
            int d = dayIndex(asOf, i - n);
            sum[d] += history[i];
            count[d]++;
            total += history[i];
        }
        double overall = n > 0 ? total / n : 0;
        double[] factor = new double[7];
        for (int d = 0; d < 7; d++) {
            factor[d] = overall > 0 && count[d] > 0 ? Math.max(0.2, (sum[d] / count[d]) / overall) : 1.0;
        }
        return factor;
    }

    private static int dayIndex(LocalDate asOf, int offset) {
        return asOf.plusDays(offset).getDayOfWeek().getValue() - 1;
    }

    private static double mean(int[] a, int from, int to) {
        from = Math.max(0, from);
        if (to <= from) {
            return 0;
        }
        double s = 0;
        for (int i = from; i < to; i++) {
            s += a[i];
        }
        return s / (to - from);
    }
}
