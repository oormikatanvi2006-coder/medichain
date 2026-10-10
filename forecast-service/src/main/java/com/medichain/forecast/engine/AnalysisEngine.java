package com.medichain.forecast.engine;

import com.medichain.forecast.client.InventoryDtos.BatchDto;
import com.medichain.forecast.client.InventoryDtos.CaseSeries;
import com.medichain.forecast.client.InventoryDtos.FacilityDto;
import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import com.medichain.forecast.client.InventoryDtos.Snapshot;
import com.medichain.forecast.client.InventoryDtos.StockLine;
import com.medichain.forecast.engine.DemandForecaster.Forecast;
import com.medichain.forecast.engine.Results.Analysis;
import com.medichain.forecast.engine.Results.BatchView;
import com.medichain.forecast.engine.Results.ExpiryAlert;
import com.medichain.forecast.engine.Results.Reorder;
import com.medichain.forecast.engine.Results.Retrieval;
import com.medichain.forecast.engine.Results.RiskRow;
import com.medichain.forecast.engine.Results.Transfer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The analysis pipeline, in plain Java with no framework dependencies:
 *
 * <pre>
 * forecast demand  ->  simulate stock day by day (first-expiry-first-out)
 *                  ->  days-to-stock-out + units that will expire unused
 *                  ->  match surplus facilities to deficit facilities (urgency, distance, expiry)
 *                  ->  reorder quantities from lead time and forecast error
 * </pre>
 */
public final class AnalysisEngine {
    /** How far ahead stock is simulated. */
    public static final int SIM_DAYS = 180;
    /** Risk bands by days of stock left. */
    public static final int CRITICAL_DAYS = 5;
    public static final int HIGH_DAYS = 10;
    public static final int MODERATE_DAYS = 21;
    /** Stock status: below the minimum is understocked, above the maximum is overstocked. */
    public static final int MIN_STOCK_DAYS = 15;
    public static final int MAX_STOCK_DAYS = 90;
    /** Window for the stock-out probability. */
    public static final int PROBABILITY_DAYS = 14;
    public static final int EXPIRING_SOON_DAYS = 90;
    /** A receiving facility is topped up to this many days of cover. */
    public static final int TARGET_COVER_DAYS = 30;
    /** A donor always keeps at least this many days of its own forecast demand. */
    public static final int DONOR_KEEP_DAYS = 45;
    /** Batches with less shelf life than this are not shipped. */
    public static final int MIN_SHELF_LIFE_DAYS = 21;
    public static final double MAX_DISTANCE_KM = 120;
    public static final int REVIEW_PERIOD_DAYS = 30;
    /** z-score for a 95% service level, used for safety stock. */
    public static final double SERVICE_Z = 1.65;
    /** Reorders due within this many days are listed. */
    public static final int REORDER_LOOKAHEAD_DAYS = 21;
    private static final int PACK_SIZE = 100;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Working state for one medicine at one facility. */
    private static final class Line {
        FacilityDto facility;
        MedicineDto medicine;
        int[] history;
        List<BatchDto> allBatches = new ArrayList<>();
        List<BatchDto> usable = new ArrayList<>();
        int stock;
        Forecast forecast;
        CaseSignal signal = CaseSignal.NONE;
        double days = Double.POSITIVE_INFINITY;
        int[] waste;
        int projectedWaste;
        int need;
        int unmet;
        int surplus;
        int[] shippable;
        int inbound;
        int outbound;
        String action;
    }

    public Analysis analyse(Snapshot snap, Set<Long> medicineIds, int horizonDays) {
        LocalDate asOf = snap.asOf();
        int horizon = Math.max(1, Math.min(horizonDays, 90));

        Map<Long, FacilityDto> facilities = new HashMap<>();
        snap.facilities().forEach(f -> facilities.put(f.id(), f));
        Map<Long, MedicineDto> medicines = new HashMap<>();
        snap.medicines().forEach(m -> medicines.put(m.id(), m));
        Map<String, CaseSignal> signals = new HashMap<>();
        int caseRecords = 0;
        Map<String, List<Integer>> caseTrends = new LinkedHashMap<>();
        for (CaseSeries c : snap.caseTrends()) {
            caseTrends.put(c.disease(), c.dailyCases());
            signals.put(c.disease(), CaseSignal.of(c.disease(), c.dailyCases()));
            caseRecords += c.dailyCases().size();
        }

        // 1. Forecast and simulate every facility-medicine line
        Map<Long, List<Line>> byMedicine = new LinkedHashMap<>();
        List<Line> lines = new ArrayList<>();
        int batchCount = 0;
        int consumptionRecords = 0;
        for (StockLine raw : snap.stock()) {
            MedicineDto m = medicines.get(raw.medicineId());
            FacilityDto f = facilities.get(raw.facilityId());
            if (m == null || f == null || (!medicineIds.isEmpty() && !medicineIds.contains(m.id()))) {
                continue;
            }
            Line l = new Line();
            l.facility = f;
            l.medicine = m;
            l.history = raw.dailyDemand().stream().mapToInt(Integer::intValue).toArray();
            l.allBatches.addAll(raw.batches());
            for (BatchDto b : raw.batches()) {
                if (b.quantity() > 0 && b.expiryDate().isAfter(asOf)) {
                    l.usable.add(b);
                    l.stock += b.quantity();
                }
            }
            l.usable.sort(Comparator.comparing(BatchDto::expiryDate));
            if (m.linkedDisease() != null && signals.containsKey(m.linkedDisease())) {
                l.signal = signals.get(m.linkedDisease());
            }
            l.forecast = DemandForecaster.forecast(l.history, asOf, SIM_DAYS, l.signal.uplift());
            simulate(l, asOf);
            lines.add(l);
            byMedicine.computeIfAbsent(m.id(), k -> new ArrayList<>()).add(l);
            batchCount += raw.batches().size();
            consumptionRecords += raw.dailyDemand().size();
        }

        // 2. Redistribution, one medicine at a time
        List<Transfer> transfers = new ArrayList<>();
        for (List<Line> group : byMedicine.values()) {
            transfers.addAll(redistribute(group, horizon, asOf));
        }

        transfers.sort(Comparator.comparingDouble(Transfer::receiverDaysBefore));

        // 3. Procurement
        List<Reorder> reorders = new ArrayList<>();
        for (Line l : lines) {
            Reorder r = reorder(l, asOf);
            if (r != null) {
                reorders.add(r);
            }
        }
        reorders.sort(Comparator.comparing(Reorder::orderBy).thenComparing(Reorder::coverDays));

        // 4. Expiry alerts and the risk table
        lines.sort(Comparator.comparingDouble((Line l) -> l.days).thenComparing(l -> l.facility.code()));
        List<ExpiryAlert> alerts = new ArrayList<>();
        List<RiskRow> risks = new ArrayList<>();
        for (Line l : lines) {
            alerts.addAll(expiryAlerts(l, asOf));
            risks.add(riskRow(l, asOf, horizon));
        }
        alerts.sort(Comparator.comparing(ExpiryAlert::expiryDate));

        Retrieval retrieval = new Retrieval(snap.facilities().size(), byMedicine.size(), lines.size(), batchCount,
                consumptionRecords, caseRecords, snap.historyDays());
        return new Analysis(asOf, snap.district(), horizon, retrieval, risks, alerts, transfers, reorders,
                caseTrends);
    }

    // ------------------------------------------------------------------ stock simulation

    /** Walks forward day by day: expired batches are written off, demand is served earliest-expiry first. */
    private static void simulate(Line l, LocalDate asOf) {
        int n = l.usable.size();
        double[] left = new double[n];
        double[] wasted = new double[n];
        for (int i = 0; i < n; i++) {
            left[i] = l.usable.get(i).quantity();
        }
        double[] daily = l.forecast.daily();
        for (int d = 0; d < SIM_DAYS; d++) {
            LocalDate date = asOf.plusDays(d);
            for (int i = 0; i < n; i++) {
                if (left[i] > 0 && !l.usable.get(i).expiryDate().isAfter(date)) {
                    wasted[i] = left[i];
                    left[i] = 0;
                }
            }
            double demand = daily[d];
            double open = demand;
            for (int i = 0; i < n && open > 0; i++) {
                double take = Math.min(left[i], open);
                left[i] -= take;
                open -= take;
            }
            if (open > 1e-6 && Double.isInfinite(l.days)) {
                l.days = d + (demand - open) / demand;
            }
        }
        l.waste = new int[n];
        for (int i = 0; i < n; i++) {
            l.waste[i] = (int) Math.floor(wasted[i]);
            l.projectedWaste += l.waste[i];
        }
    }

    // ------------------------------------------------------------------ redistribution

    private List<Transfer> redistribute(List<Line> group, int horizon, LocalDate asOf) {
        List<Line> deficits = new ArrayList<>();
        List<Line> donors = new ArrayList<>();
        for (Line l : group) {
            if (l.days <= horizon) {
                l.need = Math.max(0, (int) Math.ceil(cumulative(l, TARGET_COVER_DAYS)) - l.stock);
                if (l.need > 0) {
                    deficits.add(l);
                }
            } else if (l.days > TARGET_COVER_DAYS) {
                int spare = l.stock - (int) Math.ceil(cumulative(l, DONOR_KEEP_DAYS));
                l.shippable = new int[l.usable.size()];
                int canShip = 0;
                for (int i = 0; i < l.usable.size(); i++) {
                    if (!l.usable.get(i).expiryDate().isBefore(asOf.plusDays(MIN_SHELF_LIFE_DAYS))) {
                        l.shippable[i] = l.usable.get(i).quantity();
                        canShip += l.shippable[i];
                    }
                }
                l.surplus = Math.min(canShip, Math.max(spare, l.projectedWaste));
                if (l.surplus >= 20) {
                    donors.add(l);
                }
            }
        }
        deficits.sort(Comparator.comparingDouble(l -> l.days));

        List<Transfer> out = new ArrayList<>();
        for (Line d : deficits) {
            int originalNeed = d.need;
            // Prefer one shipment that covers the whole need, then stock that would otherwise expire,
            // then the nearest facility.
            List<Line> ranked = new ArrayList<>();
            for (Line s : donors) {
                if (distanceKm(s.facility, d.facility) <= MAX_DISTANCE_KM) {
                    ranked.add(s);
                }
            }
            ranked.sort(Comparator.comparingInt((Line s) -> (s.surplus >= originalNeed ? 0 : 2)
                            + (s.projectedWaste > 0 ? 0 : 1))
                    .thenComparingDouble(s -> distanceKm(s.facility, d.facility)));
            for (Line s : ranked) {
                if (d.need <= 0) {
                    break;
                }
                double km = distanceKm(s.facility, d.facility);
                if (s.surplus < 20) {
                    continue;
                }
                int wanted = (int) Math.ceil(d.need / 10.0) * 10;
                int qty = Math.min(wanted, s.surplus / 10 * 10);
                if (qty < Math.max(20, 0.25 * originalNeed)) {
                    continue;
                }
                out.add(buildTransfer(s, d, qty, km, asOf));
                d.need = Math.max(0, d.need - qty);
            }
            d.unmet = d.need;
            if (d.inbound == 0) {
                d.action = "No surplus within " + (int) MAX_DISTANCE_KM + " km: raise an emergency order";
            }
        }
        return out;
    }

    private Transfer buildTransfer(Line s, Line d, int qty, double km, LocalDate asOf) {
        int fromStockBefore = s.stock - s.outbound;
        int toStockBefore = d.stock + d.inbound;
        double donorBefore = coverDays(s.stock - s.outbound, s);
        double receiverBefore = Math.min(d.days, SIM_DAYS);
        double receiverCoverSoFar = d.inbound == 0 ? receiverBefore : coverDays(d.stock + d.inbound, d);
        int surplusBefore = s.surplus;

        // Ship the earliest-expiring batches that still have enough shelf life.
        List<String> batches = new ArrayList<>();
        LocalDate earliestExpiry = null;
        int open = qty;
        for (int i = 0; i < s.usable.size() && open > 0; i++) {
            int take = Math.min(open, s.shippable[i]);
            if (take <= 0) {
                continue;
            }
            BatchDto b = s.usable.get(i);
            s.shippable[i] -= take;
            open -= take;
            batches.add(b.batchNo() + " x " + qty(take) + " (expires " + b.expiryDate().format(DAY_YEAR) + ")");
            if (earliestExpiry == null) {
                earliestExpiry = b.expiryDate();
            }
        }
        s.surplus -= qty;
        s.outbound += qty;
        d.inbound += qty;
        double receiverAfter = coverDays(d.stock + d.inbound, d);
        double donorAfter = coverDays(s.stock - s.outbound, s);

        String med = d.medicine.name();
        String unit = d.medicine.unit();
        String rationale = d.facility.name() + " will stock out of " + med + " in " + f1(d.days) + " days (around "
                + asOf.plusDays((long) Math.floor(d.days)).format(DAY) + "). Transfer " + qty(qty) + " " + unit
                + " from " + s.facility.name() + ", " + f0(km) + " km away, which holds " + f0(donorBefore)
                + " days of cover. " + d.facility.name() + " rises to " + f0(receiverAfter) + " days of cover; "
                + s.facility.name() + " keeps " + f0(donorAfter) + " days.";

        List<String> factors = new ArrayList<>();
        factors.add("Remaining stock at " + d.facility.name() + ": " + qty(d.stock) + " " + unit + " = "
                + f1(d.days) + " days at forecast demand of " + f1(average(d, 10)) + "/day");
        factors.add("Consumption trend at " + d.facility.name() + ": " + signed(d.forecast.trendPct())
                + "% over the last 14 days vs the 14 before");
        if (d.signal.disease() != null) {
            factors.add("Seasonal signal: " + d.signal.disease().toLowerCase(Locale.ROOT) + " cases "
                    + signed(d.signal.momentumPct()) + "% vs 3-week baseline, forecast uplift "
                    + signed((d.signal.uplift() - 1) * 100) + "%");
        }
        factors.add("Surplus at " + s.facility.name() + ": " + qty(surplusBefore) + " " + unit + " beyond its own "
                + DONOR_KEEP_DAYS + "-day need (" + qty(s.stock) + " on hand)");
        if (s.projectedWaste > 0) {
            factors.add("Expiry: " + qty(s.projectedWaste) + " " + unit + " at " + s.facility.name()
                    + " would expire unused; this transfer puts them to use");
        }
        if (earliestExpiry != null) {
            long shelf = ChronoUnit.DAYS.between(asOf, earliestExpiry);
            factors.add("Shipped stock expires from " + earliestExpiry.format(DAY_YEAR) + " (" + shelf
                    + " days away)" + (shelf < receiverAfter
                    ? "; dispense this batch first, it is shorter than the " + f0(receiverAfter) + " days of cover"
                    : "; " + d.facility.name() + " will use it well before then"));
        }
        factors.add("Distance: " + f0(km) + " km straight-line; " + (s.projectedWaste > 0
                ? "chosen because its stock would otherwise expire"
                : "nearest facility with enough spare stock"));
        if (d.inbound > qty) {
            factors.add("Combined with an earlier transfer; cover before this one was " + f0(receiverCoverSoFar) + " days");
        }

        String label = "Transfer " + qty(qty) + " " + unit + " from " + s.facility.name() + " (" + f0(km) + " km)";
        d.action = d.action == null ? label : d.action + "; " + label;
        s.action = "Send " + qty(s.outbound) + " " + unit + " of surplus to deficit facilities";

        return new Transfer(d.medicine.id(), med, unit, s.facility.id(), s.facility.code(), s.facility.name(),
                d.facility.id(), d.facility.code(), d.facility.name(), qty, fromStockBefore,
                fromStockBefore - qty, toStockBefore, toStockBefore + qty, round1(km), round1(receiverBefore),
                round1(receiverAfter), round1(donorBefore), round1(donorAfter), riskLevel(d.days), rationale,
                factors, batches);
    }

    // ------------------------------------------------------------------ procurement

    /** Reorder point = forecast demand over the lead time + safety stock; order up to lead time + review period. */
    private Reorder reorder(Line l, LocalDate asOf) {
        int lead = Math.max(1, l.medicine.leadTimeDays());
        double safety = SERVICE_Z * l.forecast.sigma() * Math.sqrt(lead);
        double position = l.stock + l.inbound - l.outbound;

        int orderIn = -1;
        for (int d = 0; d <= REORDER_LOOKAHEAD_DAYS; d++) {
            double left = position - cumulative(l, d);
            double leadDemand = cumulative(l, d + lead) - cumulative(l, d);
            if (left <= leadDemand + safety) {
                orderIn = d;
                break;
            }
        }
        if (orderIn < 0) {
            return null;
        }
        double leftAtOrder = Math.max(0, position - cumulative(l, orderIn));
        double target = cumulative(l, orderIn + lead + REVIEW_PERIOD_DAYS) - cumulative(l, orderIn) + safety;
        int quantity = (int) Math.ceil(Math.max(0, target - leftAtOrder) / PACK_SIZE) * PACK_SIZE;
        if (quantity <= 0) {
            return null;
        }
        double cover = coverDays(position, l);
        int rop = (int) Math.round(cumulative(l, lead) + safety);
        String status = orderIn > 0 ? "ORDER_SOON" : cover < lead ? "URGENT" : "ORDER_NOW";
        LocalDate orderBy = asOf.plusDays(orderIn);
        String unit = l.medicine.unit();

        String rationale;
        if ("URGENT".equals(status)) {
            rationale = "Stock" + (l.inbound > 0 ? " (after incoming transfers)" : "") + " covers " + f1(cover)
                    + " days but supply takes " + lead + " days: order " + qty(quantity) + " " + unit
                    + " today and expedite delivery.";
        } else if ("ORDER_NOW".equals(status)) {
            rationale = "Stock position of " + qty((int) position) + " " + unit + " is at or below the reorder point of "
                    + qty(rop) + ": order " + qty(quantity) + " " + unit + " today.";
        } else {
            rationale = "Stock reaches the reorder point of " + qty(rop) + " " + unit + " in " + orderIn
                    + " days: order " + qty(quantity) + " " + unit + " by " + orderBy.format(DAY) + ".";
        }

        List<String> factors = new ArrayList<>();
        factors.add("Stock position: " + qty((int) position) + " " + unit + " (on hand " + qty(l.stock)
                + (l.inbound > 0 ? ", incoming transfers " + qty(l.inbound) : "")
                + (l.outbound > 0 ? ", outgoing transfers " + qty(l.outbound) : "") + ") = " + f1(cover) + " days");
        factors.add("Supplier lead time: " + lead + " days, forecast demand in that time "
                + qty((int) Math.round(cumulative(l, lead))) + " " + unit);
        factors.add("Safety stock: " + qty((int) Math.round(safety)) + " " + unit
                + " (95% service level, daily forecast error " + f1(l.forecast.sigma()) + ")");
        factors.add("Order covers lead time + " + REVIEW_PERIOD_DAYS + "-day review period, rounded to packs of "
                + PACK_SIZE);
        if (l.unmet > 0) {
            factors.add("Transfers could not cover " + qty(l.unmet) + " " + unit + " of the shortfall");
        }

        return new Reorder(l.facility.id(), l.facility.code(), l.facility.name(), l.medicine.id(),
                l.medicine.name(), unit, quantity, orderBy, orderBy.plusDays(lead), lead, status, round1(cover),
                rop, (int) Math.round(safety), (int) position, Math.round(quantity * l.medicine.unitCost()),
                rationale, factors);
    }

    // ------------------------------------------------------------------ expiry and risk rows

    private List<ExpiryAlert> expiryAlerts(Line l, LocalDate asOf) {
        List<ExpiryAlert> out = new ArrayList<>();
        for (int i = 0; i < l.usable.size(); i++) {
            if (l.waste[i] <= 0) {
                continue;
            }
            BatchDto b = l.usable.get(i);
            int daysLeft = (int) ChronoUnit.DAYS.between(asOf, b.expiryDate());
            String action = l.outbound > 0
                    ? qty(l.outbound) + " " + l.medicine.unit() + " recommended for transfer to deficit facilities"
                    : daysLeft < MIN_SHELF_LIFE_DAYS
                    ? "Too close to expiry to ship: dispense first"
                    : "Not needed for current shortages: dispense first and offer to neighbouring facilities";
            out.add(new ExpiryAlert(l.facility.id(), l.facility.code(), l.facility.name(), l.medicine.id(),
                    l.medicine.name(), l.medicine.unit(), b.batchNo(), b.expiryDate(), daysLeft, b.quantity(),
                    l.waste[i], Math.round(l.waste[i] * l.medicine.unitCost()), action));
        }
        return out;
    }

    private RiskRow riskRow(Line l, LocalDate asOf, int horizon) {
        boolean finite = !Double.isInfinite(l.days);
        String level = riskLevel(l.days);
        double base = finite ? 100 * (1 - Math.min(l.days, 60) / 60) : 0;
        double bump = base > 0 ? Math.max(0, Math.min(10, l.forecast.trendPct() / 4)) : 0;
        int score = (int) Math.round(Math.min(100, base + bump));
        String unit = l.medicine.unit();
        double avg = average(l, horizon);

        List<String> factors = new ArrayList<>();
        factors.add("Stock on hand: " + qty(l.stock) + " " + unit + " in " + l.usable.size() + " batch"
                + (l.usable.size() == 1 ? "" : "es"));
        factors.add("Forecast demand: " + f1(avg) + "/day over the next " + horizon + " days (last 14 days averaged "
                + f1(l.forecast.recentAvg()) + "/day)");
        factors.add("Consumption trend: " + signed(l.forecast.trendPct()) + "% vs the previous fortnight");
        if (l.signal.disease() != null) {
            factors.add("Seasonal signal: " + l.signal.disease().toLowerCase(Locale.ROOT) + " cases "
                    + signed(l.signal.momentumPct()) + "% vs 3-week baseline, forecast uplift "
                    + signed((l.signal.uplift() - 1) * 100) + "%");
        }
        factors.add(finite
                ? "Days of stock remaining: " + f1(l.days) + " (stock-out around "
                + asOf.plusDays((long) Math.floor(l.days)).format(DAY) + ")"
                : "Stock lasts beyond the " + SIM_DAYS + "-day simulation window");
        if (!l.usable.isEmpty()) {
            BatchDto first = l.usable.get(0);
            factors.add("Earliest expiry: batch " + first.batchNo() + " on " + first.expiryDate().format(DAY_YEAR)
                    + (l.projectedWaste > 0 ? "; " + qty(l.projectedWaste) + " " + unit + " likely to expire unused" : ""));
        }
        factors.add("Model: damped Holt smoothing with weekday seasonality (alpha " + l.forecast.alpha() + ", beta "
                + l.forecast.beta() + ")" + (l.forecast.wape() >= 0
                ? ", back-test error " + f0(l.forecast.wape() * 100) + "% over the last 14 days" : ""));

        List<Integer> history = new ArrayList<>();
        for (int h : l.history) {
            history.add(h);
        }

        // Chance that demand over the next 14 days exceeds usable stock. Demand is treated as normal around
        // the forecast, with spread from the model's daily error plus 10% uncertainty on the level.
        double demand14 = cumulative(l, PROBABILITY_DAYS);
        double spread = l.forecast.sigma() * Math.sqrt(PROBABILITY_DAYS) + 0.10 * demand14;
        double probability = spread <= 0 ? (demand14 > l.stock ? 1 : 0) : normalCdf((demand14 - l.stock) / spread);

        // Average monthly consumption over the last 90 days and the stock status category that
        // logistics systems report: stocked out, understocked, adequately stocked, overstocked.
        int window = Math.min(90, l.history.length);
        double total = 0;
        for (int i = l.history.length - window; i < l.history.length; i++) {
            total += l.history[i];
        }
        double amc = window == 0 ? 0 : total / window * 30;
        String stockStatus = l.stock == 0 ? "STOCKED_OUT"
                : amc == 0 ? "UNKNOWN"
                : l.days < MIN_STOCK_DAYS ? "UNDERSTOCKED"
                : l.days > MAX_STOCK_DAYS ? "OVERSTOCKED" : "ADEQUATE";

        List<BatchView> batches = new ArrayList<>();
        l.allBatches.stream().sorted(Comparator.comparing(BatchDto::expiryDate)).forEach(b -> {
            int left = (int) ChronoUnit.DAYS.between(asOf, b.expiryDate());
            batches.add(new BatchView(b.batchNo(), b.quantity(), b.manufactureDate(), b.expiryDate(), left,
                    left <= 0 ? "EXPIRED" : left <= EXPIRING_SOON_DAYS ? "EXPIRING_SOON" : "USABLE"));
        });
        List<Integer> forecast = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            forecast.add((int) Math.round(l.forecast.daily()[i]));
        }

        String action = l.action;
        if (action == null) {
            action = "LOW".equals(level) ? "None" : "Watch. See indent advice.";
        }
        return new RiskRow(l.facility.id(), l.facility.code(), l.facility.name(), l.facility.type(),
                l.medicine.id(), l.medicine.name(), unit, l.stock, Math.round(amc),
                amc > 0 ? Math.round(l.stock / amc * 100) / 100.0 : null, stockStatus, round1(avg),
                round1(l.forecast.recentAvg()),
                round1(l.forecast.trendPct()), finite ? round1(l.days) : null,
                finite ? asOf.plusDays((long) Math.floor(l.days)) : null, level, score, l.days <= horizon,
                round1(probability * 100), l.signal.disease(), round1(l.signal.momentumPct()), round1((l.signal.uplift() - 1) * 100),
                l.forecast.wape() >= 0 ? round1(l.forecast.wape() * 100) : -1, l.projectedWaste, action, factors,
                batches, history, forecast);
    }

    // ------------------------------------------------------------------ helpers

    static String riskLevel(double days) {
        if (days <= CRITICAL_DAYS) {
            return "CRITICAL";
        }
        if (days <= HIGH_DAYS) {
            return "HIGH";
        }
        if (days <= MODERATE_DAYS) {
            return "MODERATE";
        }
        return "LOW";
    }

    /** Forecast demand over the first {@code days} days. */
    private static double cumulative(Line l, int days) {
        double[] daily = l.forecast.daily();
        double sum = 0;
        for (int i = 0; i < Math.min(days, daily.length); i++) {
            sum += daily[i];
        }
        return sum;
    }

    private static double average(Line l, int days) {
        return cumulative(l, days) / Math.max(1, days);
    }

    /** Days a given quantity lasts at forecast demand (ignores expiry). */
    private static double coverDays(double stock, Line l) {
        double[] daily = l.forecast.daily();
        double left = stock;
        for (int d = 0; d < daily.length; d++) {
            if (daily[d] > left) {
                return d + (daily[d] > 0 ? left / daily[d] : 0);
            }
            left -= daily[d];
        }
        return SIM_DAYS;
    }

    /** Great-circle (haversine) distance. */
    public static double distanceKm(FacilityDto a, FacilityDto b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** Standard normal cumulative distribution (Abramowitz-Stegun approximation, error below 1e-7). */
    static double normalCdf(double z) {
        double t = 1 / (1 + 0.2316419 * Math.abs(z));
        double poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        double tail = Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI) * poly;
        return z >= 0 ? 1 - tail : tail;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String f0(double v) {
        return String.format(Locale.US, "%.0f", v);
    }

    private static String f1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static String signed(double v) {
        return String.format(Locale.US, "%+.0f", v);
    }

    private static String qty(int v) {
        return String.format(Locale.US, "%,d", v);
    }
}
