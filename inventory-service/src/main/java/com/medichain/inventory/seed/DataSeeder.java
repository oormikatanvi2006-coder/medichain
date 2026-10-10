package com.medichain.inventory.seed;

import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import com.medichain.inventory.repo.BatchRepository;
import com.medichain.inventory.repo.CaseTrendRepository;
import com.medichain.inventory.repo.ConsumptionLogRepository;
import com.medichain.inventory.repo.FacilityRepository;
import com.medichain.inventory.repo.MedicineRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Loads the simulated data into the database the first time the service starts. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final FacilityRepository facilities;
    private final MedicineRepository medicines;
    private final BatchRepository batches;
    private final ConsumptionLogRepository logs;
    private final CaseTrendRepository cases;
    private volatile boolean ready;

    /** True once the demo data is loaded; the live feed waits for this. */
    public boolean isReady() {
        return ready;
    }

    public DataSeeder(FacilityRepository facilities, MedicineRepository medicines, BatchRepository batches,
                      ConsumptionLogRepository logs, CaseTrendRepository cases) {
        this.facilities = facilities;
        this.medicines = medicines;
        this.batches = batches;
        this.logs = logs;
        this.cases = cases;
    }

    @Override
    public void run(String... args) {
        if (facilities.count() > 0) {
            ready = true;
            return;
        }
        List<Facility> fs = facilities.saveAll(DemoDataFactory.facilities());
        List<Medicine> ms = medicines.saveAll(DemoDataFactory.medicines());
        DemoDataFactory.DemoData data = DemoDataFactory.generate(fs, ms, LocalDate.now());
        batches.saveAll(data.batches());
        logs.saveAll(data.logs());
        cases.saveAll(data.cases());
        log.info("Seeded {} facilities, {} medicines, {} batches, {} consumption rows, {} case-trend rows",
                fs.size(), ms.size(), data.batches().size(), data.logs().size(), data.cases().size());
        ready = true;
    }
}
