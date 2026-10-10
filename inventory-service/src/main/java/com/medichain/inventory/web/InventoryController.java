package com.medichain.inventory.web;

import com.medichain.inventory.dto.Dtos.BatchRequest;
import com.medichain.inventory.dto.Dtos.ConsumptionRequest;
import com.medichain.inventory.dto.Dtos.Live;
import com.medichain.inventory.dto.Dtos.Snapshot;
import com.medichain.inventory.dto.Dtos.TransferRequest;
import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import com.medichain.inventory.model.StockTransfer;
import com.medichain.inventory.repo.BatchRepository;
import com.medichain.inventory.repo.ConsumptionLogRepository;
import com.medichain.inventory.repo.FacilityRepository;
import com.medichain.inventory.repo.MedicineRepository;
import com.medichain.inventory.repo.StockTransferRepository;
import com.medichain.inventory.seed.LiveFeedSimulator;
import com.medichain.inventory.service.InventoryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService service;
    private final FacilityRepository facilities;
    private final MedicineRepository medicines;
    private final BatchRepository batches;
    private final ConsumptionLogRepository logs;
    private final StockTransferRepository transfers;
    private final LiveFeedSimulator feed;

    public InventoryController(InventoryService service, FacilityRepository facilities, MedicineRepository medicines,
                               BatchRepository batches, ConsumptionLogRepository logs,
                               StockTransferRepository transfers, LiveFeedSimulator feed) {
        this.feed = feed;
        this.service = service;
        this.facilities = facilities;
        this.medicines = medicines;
        this.batches = batches;
        this.logs = logs;
        this.transfers = transfers;
    }

    // ---- Facility and medicine master ----

    @GetMapping("/districts")
    public List<String> districts() {
        return facilities.findDistricts();
    }

    @GetMapping("/facilities")
    public List<Facility> facilities(@RequestParam(name = "district", required = false) String district) {
        return district == null || district.isBlank()
                ? facilities.findAll()
                : facilities.findByDistrictIgnoreCaseOrderByCodeAsc(district);
    }

    @PostMapping("/facilities")
    public Facility addFacility(@RequestBody Facility facility) {
        if (facility.getCode() == null || facility.getCode().isBlank() || facility.getName() == null
                || facility.getName().isBlank() || facility.getDistrict() == null || facility.getDistrict().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code, name and district are required");
        }
        facility.setId(null);
        return facilities.save(facility);
    }

    @GetMapping("/medicines")
    public List<Medicine> medicines() {
        return medicines.findAll();
    }

    @PostMapping("/medicines")
    public Medicine addMedicine(@RequestBody Medicine medicine) {
        if (medicine.getCode() == null || medicine.getCode().isBlank() || medicine.getName() == null
                || medicine.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code and name are required");
        }
        if (medicine.getLinkedDisease() != null && medicine.getLinkedDisease().isBlank()) {
            medicine.setLinkedDisease(null);
        }
        medicine.setId(null);
        return medicines.save(medicine);
    }

    // ---- Stock, batches and consumption ----

    @GetMapping("/batches")
    public List<Batch> batches(@RequestParam("facilityId") Long facilityId, @RequestParam(name = "medicineId", required = false) Long medicineId) {
        return medicineId == null
                ? batches.findByFacilityIdOrderByExpiryDateAsc(facilityId)
                : batches.findByFacilityIdAndMedicineIdOrderByExpiryDateAsc(facilityId, medicineId);
    }

    @PostMapping("/batches")
    public Batch receiveBatch(@RequestBody BatchRequest request) {
        return service.receiveBatch(request);
    }

    @GetMapping("/consumption")
    public List<ConsumptionLog> consumption(@RequestParam("facilityId") Long facilityId, @RequestParam("medicineId") Long medicineId,
                                            @RequestParam(name = "days", defaultValue = "90") int days) {
        return logs.findByFacilityIdAndMedicineIdAndLogDateGreaterThanEqualOrderByLogDateAsc(
                facilityId, medicineId, LocalDate.now().minusDays(days));
    }

    @PostMapping("/consumption")
    public ConsumptionLog recordConsumption(@RequestBody ConsumptionRequest request) {
        return service.recordConsumption(request);
    }

    /** Aggregated view used by the forecast service. */
    @GetMapping("/snapshot")
    public Snapshot snapshot(@RequestParam("district") String district, @RequestParam(name = "days", defaultValue = "120") int days) {
        return service.snapshot(district, Math.max(28, Math.min(days, 365)));
    }

    /** Today's stock movement for a district: totals, issues per minute and the latest ledger lines. */
    @GetMapping("/live")
    public Live live(@RequestParam("district") String district,
                     @RequestParam(name = "minutes", defaultValue = "30") int minutes) {
        return service.live(district, Math.max(5, Math.min(minutes, 120)), feed.isEnabled(), feed.getSpeed());
    }

    // ---- Transfers ----

    @GetMapping("/transfers")
    public List<StockTransfer> transfers() {
        return transfers.findAll();
    }

    @PostMapping("/transfers")
    public StockTransfer transfer(@RequestBody TransferRequest request) {
        return service.transfer(request);
    }
}
