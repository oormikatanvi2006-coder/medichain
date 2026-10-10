package com.medichain.inventory.seed;

import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import com.medichain.inventory.repo.ConsumptionLogRepository;
import com.medichain.inventory.repo.FacilityRepository;
import com.medichain.inventory.repo.MedicineRepository;
import com.medichain.inventory.service.InventoryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simulated dispensing feed. In a real deployment facilities post their issues as they happen; here a
 * timer does it, so stock on hand, today's fill rate and the stock ledger change while you watch.
 *
 * <p>Each line (facility + medicine) is issued at its own recent daily rate multiplied by
 * {@code medichain.live-feed.speed}. Speed 1 is real time; the default of 12 makes movement visible in a demo.
 * On start-up the last 45 minutes are filled in, so the ledger and the per-minute chart are not empty.
 */
@Component
public class LiveFeedSimulator {
    private static final int PICKS_PER_TICK = 12;
    private static final int BACKFILL_MINUTES = 45;
    private static final double ISSUES_PER_LINE_PER_DAY = 12;

    private final FacilityRepository facilities;
    private final MedicineRepository medicines;
    private final ConsumptionLogRepository logs;
    private final InventoryService service;
    private final DataSeeder seeder;
    private final boolean enabled;
    private final double speed;
    private final long intervalMs;
    private final Random random = new Random();
    private final Map<String, Double> dailyDemand = new ConcurrentHashMap<>();
    private boolean backfilled;

    public LiveFeedSimulator(FacilityRepository facilities, MedicineRepository medicines,
                             ConsumptionLogRepository logs, InventoryService service, DataSeeder seeder,
                             @Value("${medichain.live-feed.enabled:true}") boolean enabled,
                             @Value("${medichain.live-feed.speed:12}") double speed,
                             @Value("${medichain.live-feed.interval-ms:5000}") long intervalMs) {
        this.facilities = facilities;
        this.medicines = medicines;
        this.logs = logs;
        this.service = service;
        this.seeder = seeder;
        this.enabled = enabled;
        this.speed = speed;
        this.intervalMs = Math.max(500, intervalMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeed() {
        return speed;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    @Scheduled(fixedDelayString = "${medichain.live-feed.interval-ms:5000}",
            initialDelayString = "${medichain.live-feed.interval-ms:5000}")
    public void tick() {
        if (!enabled || !seeder.isReady()) {
            return;
        }
        if (!backfilled) {
            backfilled = true;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime from = now.minusMinutes(BACKFILL_MINUTES);
            if (from.toLocalDate().isBefore(now.toLocalDate())) {
                from = now.toLocalDate().atStartOfDay();
            }
            for (LocalDateTime t = from; t.isBefore(now); t = t.plusNanos(intervalMs * 1_000_000L)) {
                issueAt(t);
            }
            return;
        }
        issueAt(LocalDateTime.now());
    }

    private void issueAt(LocalDateTime at) {
        List<Facility> fs = facilities.findAll();
        List<Medicine> ms = medicines.findAll();
        if (fs.isEmpty() || ms.isEmpty()) {
            return;
        }
        double ticksPerDay = 86_400_000.0 / intervalMs;
        double chance = speed * ISSUES_PER_LINE_PER_DAY * fs.size() * ms.size() / (ticksPerDay * PICKS_PER_TICK);
        for (int i = 0; i < PICKS_PER_TICK; i++) {
            if (random.nextDouble() > chance) {
                continue;
            }
            Facility f = fs.get(random.nextInt(fs.size()));
            Medicine m = ms.get(random.nextInt(ms.size()));
            double daily = recentDailyDemand(f.getId(), m.getId());
            if (daily <= 0) {
                continue;
            }
            double units = daily / ISSUES_PER_LINE_PER_DAY * (0.5 + random.nextDouble());
            int quantity = (int) units + (random.nextDouble() < units - (int) units ? 1 : 0);
            if (quantity > 0) {
                service.issueFromFeed(f.getId(), m.getId(), quantity, at);
            }
        }
    }

    /** Average demand per day over the last 14 full days. */
    private double recentDailyDemand(Long facilityId, Long medicineId) {
        String key = facilityId + ":" + medicineId;
        Double cached = dailyDemand.get(key);
        if (cached != null) {
            return cached;
        }
        LocalDate today = LocalDate.now();
        double total = 0;
        for (ConsumptionLog log : logs.findByFacilityIdAndMedicineIdAndLogDateGreaterThanEqualOrderByLogDateAsc(
                facilityId, medicineId, today.minusDays(14))) {
            if (log.getLogDate().isBefore(today)) {
                total += log.getDemanded();
            }
        }
        double daily = total / 14.0;
        if (daily > 0) {
            dailyDemand.put(key, daily);
        }
        return daily;
    }
}
