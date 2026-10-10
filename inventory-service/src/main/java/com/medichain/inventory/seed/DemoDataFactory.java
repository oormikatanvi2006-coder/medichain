package com.medichain.inventory.seed;

import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.CaseTrend;
import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the simulated district: facilities, an essential medicines list, 120 days of consumption
 * that follows seasonal disease curves, and batch-level stock.
 *
 * <p>All dates are relative to "today", so the demo story is the same whenever the service starts:
 * Government Hospital Vellore is about to run out of paracetamol while PHC Katpadi, 12 km away,
 * holds surplus that is partly close to expiry.
 */
public final class DemoDataFactory {
    public static final int HISTORY_DAYS = 120;
    private static final DateTimeFormatter YYMM = DateTimeFormatter.ofPattern("yyMM");

    private DemoDataFactory() {
    }

    public record DemoData(List<Batch> batches, List<ConsumptionLog> logs, List<CaseTrend> cases) {
    }

    public static List<Facility> facilities() {
        return List.of(
                new Facility("GH-VLR", "Government Hospital – Vellore", "GH", "Vellore", 12.9165, 79.1325),
                new Facility("PHC-KPD", "PHC – Katpadi", "PHC", "Vellore", 13.0200, 79.1600),
                new Facility("CHC-GDM", "CHC – Gudiyatham", "CHC", "Vellore", 12.9447, 78.8709),
                new Facility("PHC-ANC", "PHC – Anaicut", "PHC", "Vellore", 12.8790, 78.9690),
                new Facility("PHC-WLJ", "PHC – Walajah", "PHC", "Vellore", 12.9251, 79.3668),
                new Facility("PHC-KVK", "PHC – K.V. Kuppam", "PHC", "Vellore", 12.9420, 78.9960),
                new Facility("PHC-PNP", "PHC – Pernambut", "PHC", "Vellore", 12.9390, 78.7180),
                new Facility("CHC-ARC", "CHC – Arcot", "CHC", "Vellore", 12.9050, 79.3190),
                new Facility("PHC-KNB", "PHC – Kaniyambadi", "PHC", "Vellore", 12.8120, 79.1340),
                new Facility("PHC-PLK", "PHC – Pallikonda", "PHC", "Vellore", 12.9080, 78.9420),
                new Facility("PHC-SLR", "PHC – Sholinghur", "PHC", "Vellore", 13.1120, 79.4210),
                new Facility("CHC-TMR", "CHC – Timiri", "CHC", "Vellore", 12.8290, 79.3080),
                new Facility("GH-TVM", "Government Hospital – Tiruvannamalai", "GH", "Tiruvannamalai", 12.2253, 79.0747),
                new Facility("PHC-CGM", "PHC – Chengam", "PHC", "Tiruvannamalai", 12.3080, 78.7920),
                new Facility("PHC-PLR", "PHC – Polur", "PHC", "Tiruvannamalai", 12.5120, 79.1250),
                new Facility("CHC-ARN", "CHC – Arani", "CHC", "Tiruvannamalai", 12.6680, 79.2850),
                // Districts below get an unscripted mix of shortages and surpluses (see randomStory)
                new Facility("GH-CHN", "Government General Hospital – Chennai", "GH", "Chennai", 13.0815, 80.2770),
                new Facility("CHC-PRB", "CHC – Perambur", "CHC", "Chennai", 13.1170, 80.2330),
                new Facility("UPHC-ADY", "UPHC – Adyar", "PHC", "Chennai", 13.0067, 80.2570),
                new Facility("UPHC-TDP", "UPHC – Tondiarpet", "PHC", "Chennai", 13.1260, 80.2880),
                new Facility("UPHC-VLC", "UPHC – Velachery", "PHC", "Chennai", 12.9790, 80.2210),
                new Facility("GH-CBE", "Government Hospital – Coimbatore", "GH", "Coimbatore", 11.0018, 76.9629),
                new Facility("CHC-PLC", "CHC – Pollachi", "CHC", "Coimbatore", 10.6580, 77.0080),
                new Facility("PHC-MTP", "PHC – Mettupalayam", "PHC", "Coimbatore", 11.2990, 76.9350),
                new Facility("PHC-SUL", "PHC – Sulur", "PHC", "Coimbatore", 11.0250, 77.1250),
                new Facility("PHC-ANR", "PHC – Annur", "PHC", "Coimbatore", 11.2340, 77.1060),
                new Facility("GH-MDU", "Government Rajaji Hospital – Madurai", "GH", "Madurai", 9.9252, 78.1198),
                new Facility("CHC-MLR", "CHC – Melur", "CHC", "Madurai", 10.0310, 78.3380),
                new Facility("PHC-TMG", "PHC – Thirumangalam", "PHC", "Madurai", 9.8220, 77.9890),
                new Facility("PHC-USP", "PHC – Usilampatti", "PHC", "Madurai", 9.9650, 77.7880),
                new Facility("PHC-VDP", "PHC – Vadipatti", "PHC", "Madurai", 10.0840, 77.9620),
                new Facility("GH-TRY", "Government Hospital – Tiruchirappalli", "GH", "Tiruchirappalli", 10.7905, 78.7047),
                new Facility("CHC-MNP", "CHC – Manapparai", "CHC", "Tiruchirappalli", 10.6070, 78.4250),
                new Facility("PHC-LLG", "PHC – Lalgudi", "PHC", "Tiruchirappalli", 10.8740, 78.8190),
                new Facility("PHC-MSR", "PHC – Musiri", "PHC", "Tiruchirappalli", 10.9540, 78.4440),
                new Facility("PHC-TRY", "PHC – Thuraiyur", "PHC", "Tiruchirappalli", 11.1490, 78.5980),
                new Facility("GH-SLM", "Government Hospital – Salem", "GH", "Salem", 11.6643, 78.1460),
                new Facility("CHC-ATR", "CHC – Attur", "CHC", "Salem", 11.5940, 78.6010),
                new Facility("PHC-MTR", "PHC – Mettur", "PHC", "Salem", 11.7860, 77.8010),
                new Facility("PHC-OML", "PHC – Omalur", "PHC", "Salem", 11.7450, 78.0470),
                new Facility("PHC-SNK", "PHC – Sankari", "PHC", "Salem", 11.4740, 77.8690),
                new Facility("GH-TNV", "Government Hospital – Tirunelveli", "GH", "Tirunelveli", 8.7139, 77.7567),
                new Facility("CHC-ASM", "CHC – Ambasamudram", "CHC", "Tirunelveli", 8.7100, 77.4520),
                new Facility("PHC-NGN", "PHC – Nanguneri", "PHC", "Tirunelveli", 8.4930, 77.6580),
                new Facility("PHC-CMD", "PHC – Cheranmahadevi", "PHC", "Tirunelveli", 8.6770, 77.5640),
                new Facility("GH-TNJ", "Government Hospital – Thanjavur", "GH", "Thanjavur", 10.7870, 79.1378),
                new Facility("CHC-KMB", "CHC – Kumbakonam", "CHC", "Thanjavur", 10.9620, 79.3880),
                new Facility("PHC-PKT", "PHC – Pattukkottai", "PHC", "Thanjavur", 10.4250, 79.3190),
                new Facility("PHC-ORT", "PHC – Orathanadu", "PHC", "Thanjavur", 10.6230, 79.2530),
                new Facility("GH-KPM", "Government Hospital – Kancheepuram", "GH", "Kancheepuram", 12.8342, 79.7036),
                new Facility("CHC-SPR", "CHC – Sriperumbudur", "CHC", "Kancheepuram", 12.9680, 79.9420),
                new Facility("PHC-UTR", "PHC – Uthiramerur", "PHC", "Kancheepuram", 12.6160, 79.7590),
                new Facility("PHC-WJB", "PHC – Walajabad", "PHC", "Kancheepuram", 12.7930, 79.8200));
    }

    /** Ten items from India's National List of Essential Medicines 2022. */
    public static List<Medicine> medicines() {
        return List.of(
                new Medicine("PCM", "Paracetamol 500 mg (Tablet)", "Analgesic / antipyretic", "tablets",
                        "paracetamol,pcm,crocin,acetaminophen", "DENGUE", 14, 0.9, 30),
                new Medicine("ORS", "ORS (Sachet)", "Rehydration", "sachets",
                        "ors,oral rehydration,rehydration salts", "DIARRHOEA", 10, 4.5, 20),
                new Medicine("AMX", "Amoxicillin 500 mg (Capsule)", "Antibiotic", "capsules",
                        "amoxicillin,amoxycillin,amox", "ARI", 14, 2.8, 18),
                new Medicine("ZNC", "Zinc sulphate 20 mg (Tablet)", "Antidiarrhoeal", "tablets",
                        "zinc,zinc sulphate,zinc sulfate", "DIARRHOEA", 14, 1.2, 12),
                new Medicine("ALU", "Artemether + Lumefantrine (Tablet)", "Antimalarial", "tablets",
                        "artemether,lumefantrine,coartem,antimalarial", "MALARIA", 21, 9.5, 6),
                new Medicine("MET", "Metformin 500 mg (Tablet)", "Antidiabetic", "tablets",
                        "metformin", null, 14, 0.8, 16),
                new Medicine("AML", "Amlodipine 5 mg (Tablet)", "Antihypertensive", "tablets",
                        "amlodipine", null, 14, 0.6, 14),
                new Medicine("IFA", "Iron + Folic Acid (Tablet)", "Anti-anaemia", "tablets",
                        "iron,folic acid,ifa", null, 21, 0.4, 24),
                new Medicine("CTZ", "Cetirizine 10 mg (Tablet)", "Antihistamine", "tablets",
                        "cetirizine,cetrizine", "ARI", 14, 0.5, 10),
                new Medicine("AZM", "Azithromycin 500 mg (Tablet)", "Antibiotic", "tablets",
                        "azithromycin,azithro", "ARI", 14, 11.0, 8));
    }

    /** Batches for a facility-medicine pair as {days of cover, days until expiry}. */
    private static final Map<String, double[][]> STOCK_STORY = new HashMap<>();
    /** Past stock-out episodes as {first day offset, last day offset, percent of demand served}. */
    private static final Map<String, int[]> PAST_STOCKOUTS = new HashMap<>();
    /** Stock that has already expired on the shelf as {quantity, days since expiry}. */
    private static final Map<String, int[]> EXPIRED_STOCK = new HashMap<>();

    /** Districts whose stock position is written by hand below; the rest use randomStory. */
    private static final java.util.Set<String> SCRIPTED_DISTRICTS = java.util.Set.of("Vellore", "Tiruvannamalai");

    static {
        // Deficits
        STOCK_STORY.put("GH-VLR|PCM", new double[][] {{2.0, 82}, {1.4, 420}, {1.0, 570}});
        STOCK_STORY.put("PHC-WLJ|PCM", new double[][] {{9, 300}});
        STOCK_STORY.put("CHC-GDM|PCM", new double[][] {{15, 280}});
        STOCK_STORY.put("PHC-ANC|PCM", new double[][] {{12, 200}, {20, 430}});
        STOCK_STORY.put("GH-VLR|ORS", new double[][] {{10, 260}});
        STOCK_STORY.put("PHC-PNP|ORS", new double[][] {{6, 240}});
        STOCK_STORY.put("PHC-KVK|AMX", new double[][] {{7, 200}});
        STOCK_STORY.put("PHC-PLR|ORS", new double[][] {{7, 220}});
        // Surplus, some of it close to expiry
        STOCK_STORY.put("PHC-KPD|PCM", new double[][] {{60, 48}, {70, 415}});
        STOCK_STORY.put("PHC-KPD|ORS", new double[][] {{60, 150}, {80, 400}});
        STOCK_STORY.put("CHC-ARC|AMX", new double[][] {{70, 28}, {45, 380}});
        STOCK_STORY.put("CHC-TMR|ZNC", new double[][] {{80, 33}, {30, 300}});
        STOCK_STORY.put("CHC-ARC|PCM", new double[][] {{35, 200}, {55, 450}});
        STOCK_STORY.put("PHC-KNB|ORS", new double[][] {{40, 180}, {50, 420}});
        STOCK_STORY.put("GH-VLR|AMX", new double[][] {{75, 330}});
        STOCK_STORY.put("GH-TVM|ORS", new double[][] {{90, 300}});

        PAST_STOCKOUTS.put("PHC-PNP|ORS", new int[] {-27, -24, 0});
        PAST_STOCKOUTS.put("GH-VLR|PCM", new int[] {-44, -43, 50});
        PAST_STOCKOUTS.put("PHC-ANC|AMX", new int[] {-18, -16, 0});
        PAST_STOCKOUTS.put("CHC-GDM|IFA", new int[] {-63, -59, 0});
        PAST_STOCKOUTS.put("PHC-KVK|MET", new int[] {-9, -8, 30});
        PAST_STOCKOUTS.put("PHC-WLJ|ORS", new int[] {-75, -68, 20});
        PAST_STOCKOUTS.put("PHC-PLK|PCM", new int[] {-52, -46, 0});
        PAST_STOCKOUTS.put("CHC-TMR|AMX", new int[] {-36, -30, 40});
        PAST_STOCKOUTS.put("PHC-KVK|AMX", new int[] {-22, -17, 0});
        PAST_STOCKOUTS.put("PHC-SLR|ZNC", new int[] {-14, -9, 0});
        PAST_STOCKOUTS.put("CHC-ARC|PCM", new int[] {-66, -62, 50});
        PAST_STOCKOUTS.put("PHC-KPD|AZM", new int[] {-29, -20, 0});
        PAST_STOCKOUTS.put("PHC-KNB|AML", new int[] {-12, -6, 25});
        PAST_STOCKOUTS.put("PHC-PNP|IFA", new int[] {-84, -78, 0});
        PAST_STOCKOUTS.put("PHC-CGM|PCM", new int[] {-19, -14, 0});

        EXPIRED_STOCK.put("PHC-KVK|ZNC", new int[] {300, 12});
        EXPIRED_STOCK.put("CHC-TMR|CTZ", new int[] {450, 5});
        EXPIRED_STOCK.put("PHC-PNP|ALU", new int[] {120, 20});
    }

    public static DemoData generate(List<Facility> facilities, List<Medicine> medicines, LocalDate today) {
        List<Batch> batches = new ArrayList<>();
        List<ConsumptionLog> logs = new ArrayList<>();
        List<CaseTrend> cases = new ArrayList<>();

        // 1. District disease surveillance: daily case counts
        Map<String, Double> districtScale = new HashMap<>();
        for (Facility f : facilities) {
            districtScale.merge(f.getDistrict(), sizeOf(f.getType()) * 0.35, Double::sum);
        }
        String[] diseases = {"DENGUE", "DIARRHOEA", "ARI", "MALARIA"};
        double[] baseCases = {9, 16, 24, 7};
        for (Map.Entry<String, Double> d : districtScale.entrySet()) {
            // One generator per district and per facility, so adding a district never changes another's data.
            Random rnd = new Random(d.getKey().hashCode() * 131L + 17);
            for (int i = 0; i < diseases.length; i++) {
                for (int t = -HISTORY_DAYS; t <= -1; t++) {
                    double mean = baseCases[i] * d.getValue() * diseaseCurve(diseases[i], t);
                    int n = (int) Math.round(mean * (0.85 + 0.30 * rnd.nextDouble()));
                    cases.add(new CaseTrend(d.getKey(), diseases[i], today.plusDays(t), Math.max(0, n)));
                }
            }
        }

        // 2. Consumption and stock for each facility-medicine pair
        for (Facility f : facilities) {
            Random rnd = new Random(f.getCode().hashCode() * 131L + 20261010L);
            double facilitySize = sizeOf(f.getType()) * (0.85 + 0.35 * rnd.nextDouble());
            for (Medicine m : medicines) {
                String key = f.getCode() + "|" + m.getCode();
                double base = m.getBaseDailyDemand() * facilitySize * (0.9 + 0.2 * rnd.nextDouble());
                int[] stockout = PAST_STOCKOUTS.get(key);

                for (int t = -HISTORY_DAYS; t <= -1; t++) {
                    LocalDate date = today.plusDays(t);
                    double mean = base * weekdayFactor(date.getDayOfWeek()) * demandFactor(m.getLinkedDisease(), t);
                    int demanded = (int) Math.max(0, Math.round(mean * (0.90 + 0.20 * rnd.nextDouble())));
                    int dispensed = demanded;
                    if (stockout != null && t >= stockout[0] && t <= stockout[1]) {
                        dispensed = demanded * stockout[2] / 100;
                    }
                    logs.add(new ConsumptionLog(f.getId(), m.getId(), date, demanded, dispensed));
                }

                double dailyNow = base * 0.94 * demandFactor(m.getLinkedDisease(), 0);
                double[][] story = STOCK_STORY.get(key);
                if (story == null && !SCRIPTED_DISTRICTS.contains(f.getDistrict())) {
                    story = randomStory(key);
                }
                if (story == null) {
                    double cover = 32 + 40 * rnd.nextDouble();
                    story = new double[][] {
                            {cover * 0.4, 130 + rnd.nextInt(80)},
                            {cover * 0.6, 260 + rnd.nextInt(240)}};
                }
                int n = 0;
                for (double[] b : story) {
                    int qty = (int) (Math.round(b[0] * dailyNow / 10.0) * 10);
                    LocalDate expiry = today.plusDays((long) b[1]);
                    batches.add(new Batch(f.getId(), m.getId(), batchNo(m, expiry, n++), Math.max(10, qty),
                            manufactured(expiry), expiry, today.minusDays(20 + rnd.nextInt(60))));
                }
                int[] expired = EXPIRED_STOCK.get(key);
                if (expired != null) {
                    LocalDate expiry = today.minusDays(expired[1]);
                    batches.add(new Batch(f.getId(), m.getId(), batchNo(m, expiry, 9), expired[0],
                            manufactured(expiry), expiry, today.minusDays(400)));
                }
            }
        }
        return new DemoData(batches, logs, cases);
    }

    /**
     * For districts without a hand-written story: about 1 stock line in 12 is short and 1 in 8 holds
     * surplus with a batch close to expiry. Seeded by the line itself, so it is the same on every start.
     */
    private static double[][] randomStory(String key) {
        Random dice = new Random(key.hashCode() * 31L + 7);
        double p = dice.nextDouble();
        if (p < 0.085) {
            return new double[][] {{4 + 7 * dice.nextDouble(), 200 + dice.nextInt(200)}};
        }
        if (p < 0.21) {
            return new double[][] {
                    {45 + 30 * dice.nextDouble(), 30 + dice.nextInt(45)},
                    {50 + 30 * dice.nextDouble(), 330 + dice.nextInt(150)}};
        }
        return null;
    }

    /** Seasonal case curve relative to baseline; t is days from today (negative = past). */
    static double diseaseCurve(String disease, int t) {
        switch (disease) {
            case "DENGUE":      // post-monsoon surge over the last 4 weeks
                return t <= -28 ? 1.0 : 1.0 + 0.7 * (t + 28) / 28.0;
            case "DIARRHOEA":   // late-monsoon rise over the last 2.5 weeks
                return t <= -18 ? 1.0 : 1.0 + 0.35 * (t + 18) / 18.0;
            case "ARI":         // slow climb towards winter
                return 1.0 + 0.15 * (t + HISTORY_DAYS) / (double) HISTORY_DAYS;
            case "MALARIA":     // tailing off
                return 1.2 - 0.4 * (t + HISTORY_DAYS) / (double) HISTORY_DAYS;
            default:
                return 1.0;
        }
    }

    private static double demandFactor(String disease, int t) {
        return disease == null ? 1.0 : Math.pow(diseaseCurve(disease, t), 0.8);
    }

    private static double weekdayFactor(DayOfWeek d) {
        switch (d) {
            case SUNDAY: return 0.45;
            case SATURDAY: return 0.80;
            case MONDAY: return 1.15;
            default: return 1.0;
        }
    }

    private static double sizeOf(String type) {
        switch (type) {
            case "GH": return 3.0;
            case "CHC": return 1.6;
            default: return 1.0;
        }
    }

    /** Two-year shelf life, made on the first of the month. */
    private static LocalDate manufactured(LocalDate expiry) {
        return expiry.minusMonths(24).withDayOfMonth(1);
    }

    private static String batchNo(Medicine m, LocalDate expiry, int n) {
        return m.getCode() + "-" + expiry.minusMonths(24).format(YYMM) + (char) ('A' + n);
    }
}
