package com.medichain.inventory.service;

import com.medichain.inventory.dto.Dtos.BatchRequest;
import com.medichain.inventory.dto.Dtos.ConsumptionRequest;
import com.medichain.inventory.dto.Dtos.Live;
import com.medichain.inventory.dto.Dtos.LiveTxn;
import com.medichain.inventory.dto.Dtos.MinuteBucket;
import com.medichain.inventory.dto.Dtos.Snapshot;
import com.medichain.inventory.dto.Dtos.TransferRequest;
import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import com.medichain.inventory.model.StockTransaction;
import com.medichain.inventory.model.StockTransfer;
import com.medichain.inventory.repo.BatchRepository;
import com.medichain.inventory.repo.CaseTrendRepository;
import com.medichain.inventory.repo.ConsumptionLogRepository;
import com.medichain.inventory.repo.FacilityRepository;
import com.medichain.inventory.repo.MedicineRepository;
import com.medichain.inventory.repo.StockTransactionRepository;
import com.medichain.inventory.repo.StockTransferRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final FacilityRepository facilities;
    private final MedicineRepository medicines;
    private final BatchRepository batches;
    private final ConsumptionLogRepository logs;
    private final CaseTrendRepository cases;
    private final StockTransferRepository transfers;
    private final StockTransactionRepository ledger;

    public InventoryService(FacilityRepository facilities, MedicineRepository medicines, BatchRepository batches,
                            ConsumptionLogRepository logs, CaseTrendRepository cases,
                            StockTransferRepository transfers, StockTransactionRepository ledger) {
        this.ledger = ledger;
        this.facilities = facilities;
        this.medicines = medicines;
        this.batches = batches;
        this.logs = logs;
        this.cases = cases;
        this.transfers = transfers;
    }

    /** Stock, batches, consumption history and disease trends for a whole district in one response. */
    @Transactional(readOnly = true)
    public Snapshot snapshot(String district, int days) {
        List<Facility> fs = facilities.findByDistrictIgnoreCaseOrderByCodeAsc(district);
        if (fs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No facilities in district " + district);
        }
        List<Long> ids = fs.stream().map(Facility::getId).toList();
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days);
        List<Medicine> ms = medicines.findAll();
        return SnapshotAssembler.build(today, fs.get(0).getDistrict(), days, fs, ms,
                batches.findByFacilityIdIn(ids),
                logs.findByFacilityIdInAndLogDateGreaterThanEqual(ids, from),
                cases.findByDistrictIgnoreCaseAndReportDateGreaterThanEqual(district, from));
    }

    /** Moves stock between facilities, earliest-expiring batches first, keeping batch numbers and expiry dates. */
    @Transactional
    public StockTransfer transfer(TransferRequest req) {
        if (req.quantity() <= 0 || req.fromFacilityId() == null || req.toFacilityId() == null
                || req.medicineId() == null || req.fromFacilityId().equals(req.toFacilityId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transfer request");
        }
        LocalDate today = LocalDate.now();
        int shelf = req.minShelfLifeDays() == null ? 0 : req.minShelfLifeDays();
        List<Batch> usable = batches
                .findByFacilityIdAndMedicineIdOrderByExpiryDateAsc(req.fromFacilityId(), req.medicineId())
                .stream().filter(b -> b.getQuantity() > 0 && b.getExpiryDate().isAfter(today)).toList();
        List<Batch> source = new ArrayList<>(
                usable.stream().filter(b -> !b.getExpiryDate().isBefore(today.plusDays(shelf))).toList());
        if (source.stream().mapToInt(Batch::getQuantity).sum() < req.quantity()) {
            source = new ArrayList<>(usable);
        }
        if (source.stream().mapToInt(Batch::getQuantity).sum() < req.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source facility does not hold enough usable stock");
        }
        int remaining = req.quantity();
        for (Batch b : source) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, b.getQuantity());
            b.setQuantity(b.getQuantity() - take);
            batches.save(b);
            batches.save(new Batch(req.toFacilityId(), req.medicineId(), b.getBatchNo(), take,
                    b.getManufactureDate(), b.getExpiryDate(), today));
            remaining -= take;
        }
        StockTransfer saved = transfers.save(new StockTransfer(req.fromFacilityId(), req.toFacilityId(),
                req.medicineId(), req.quantity(), LocalDateTime.now(), req.reference()));
        String ref = "Transfer #" + saved.getId();
        ledger.save(new StockTransaction(LocalDateTime.now(), req.fromFacilityId(), req.medicineId(),
                StockTransaction.TRANSFER_OUT, req.quantity(), 0, usableStock(req.fromFacilityId(), req.medicineId()), ref));
        ledger.save(new StockTransaction(LocalDateTime.now(), req.toFacilityId(), req.medicineId(),
                StockTransaction.TRANSFER_IN, req.quantity(), 0, usableStock(req.toFacilityId(), req.medicineId()), ref));
        return saved;
    }

    /** Records a day's dispensing and deducts it from stock, earliest-expiring batch first. */
    @Transactional
    public ConsumptionLog recordConsumption(ConsumptionRequest req) {
        return recordConsumption(req, "Counter issue", LocalDateTime.now());
    }

    /** Called by the simulated dispensing feed: issues what stock allows and records any shortfall. */
    @Transactional
    public ConsumptionLog issueFromFeed(Long facilityId, Long medicineId, int demanded, LocalDateTime at) {
        int usable = usableStock(facilityId, medicineId);
        return recordConsumption(new ConsumptionRequest(facilityId, medicineId, null, demanded,
                Math.min(demanded, usable)), "Dispensing feed", at);
    }

    /** Units in batches that have not expired. */
    public int usableStock(Long facilityId, Long medicineId) {
        LocalDate today = LocalDate.now();
        return batches.findByFacilityIdAndMedicineIdOrderByExpiryDateAsc(facilityId, medicineId).stream()
                .filter(b -> b.getExpiryDate().isAfter(today)).mapToInt(Batch::getQuantity).sum();
    }

    private ConsumptionLog recordConsumption(ConsumptionRequest req, String reference, LocalDateTime at) {
        if (req.dispensed() < 0 || req.demanded() < 0 || (req.dispensed() == 0 && req.demanded() == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the units demanded and dispensed");
        }
        LocalDate date = req.date() == null ? LocalDate.now() : req.date();
        List<ConsumptionLog> existing =
                logs.findByFacilityIdAndMedicineIdAndLogDate(req.facilityId(), req.medicineId(), date);
        ConsumptionLog log = existing.isEmpty()
                ? new ConsumptionLog(req.facilityId(), req.medicineId(), date, 0, 0)
                : existing.get(0);
        log.setDemanded(log.getDemanded() + Math.max(req.demanded(), req.dispensed()));
        log.setDispensed(log.getDispensed() + req.dispensed());

        int remaining = req.dispensed();
        LocalDate today = LocalDate.now();
        for (Batch b : batches.findByFacilityIdAndMedicineIdOrderByExpiryDateAsc(req.facilityId(), req.medicineId())) {
            if (remaining == 0) {
                break;
            }
            if (b.getQuantity() <= 0 || !b.getExpiryDate().isAfter(today)) {
                continue;
            }
            int take = Math.min(remaining, b.getQuantity());
            b.setQuantity(b.getQuantity() - take);
            batches.save(b);
            remaining -= take;
        }
        if (remaining > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot dispense more than the usable stock on hand");
        }
        int demanded = Math.max(req.demanded(), req.dispensed());
        ledger.save(new StockTransaction(at, req.facilityId(), req.medicineId(),
                StockTransaction.ISSUE, req.dispensed(), demanded - req.dispensed(),
                usableStock(req.facilityId(), req.medicineId()), reference));
        return logs.save(log);
    }

    @Transactional
    public Batch receiveBatch(BatchRequest req) {
        if (req.quantity() <= 0 || req.expiryDate() == null || req.batchNo() == null || req.batchNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch number, quantity and expiry date are required");
        }
        if (!facilities.existsById(req.facilityId()) || !medicines.existsById(req.medicineId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown facility or medicine");
        }
        Batch saved = batches.save(new Batch(req.facilityId(), req.medicineId(), req.batchNo().trim(),
                req.quantity(), req.manufactureDate(), req.expiryDate(), LocalDate.now()));
        ledger.save(new StockTransaction(LocalDateTime.now(), req.facilityId(), req.medicineId(),
                StockTransaction.RECEIPT, req.quantity(), 0, usableStock(req.facilityId(), req.medicineId()),
                "Batch " + saved.getBatchNo()));
        return saved;
    }

    /** Today's issues, receipts and transfers for a district, with a per-minute series for the last N minutes. */
    @Transactional(readOnly = true)
    public Live live(String district, int minutes, boolean feedEnabled, double feedSpeed) {
        List<Facility> fs = facilities.findByDistrictIgnoreCaseOrderByCodeAsc(district);
        if (fs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No facilities in district " + district);
        }
        Map<Long, Facility> facilityById = new HashMap<>();
        fs.forEach(f -> facilityById.put(f.getId(), f));
        Map<Long, Medicine> medicineById = new HashMap<>();
        medicines.findAll().forEach(m -> medicineById.put(m.getId(), m));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(minutes - 1L);
        int[] units = new int[minutes];
        int[] count = new int[minutes];
        int issued = 0;
        int demanded = 0;
        int issues = 0;
        int shorts = 0;
        int received = 0;
        int transferred = 0;
        List<LiveTxn> recent = new ArrayList<>();
        LocalDateTime since = null;
        for (StockTransaction t : ledger.findByFacilityIdInAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                facilityById.keySet(), LocalDate.now().atStartOfDay())) {
            since = t.getOccurredAt();
            switch (t.getTxnType()) {
                case StockTransaction.ISSUE:
                    issued += t.getQuantity();
                    demanded += t.getQuantity() + t.getShortfall();
                    issues++;
                    if (t.getShortfall() > 0) {
                        shorts++;
                    }
                    int slot = (int) ChronoUnit.MINUTES.between(windowStart, t.getOccurredAt());
                    if (slot >= 0 && slot < minutes) {
                        units[slot] += t.getQuantity();
                        count[slot]++;
                    }
                    break;
                case StockTransaction.RECEIPT:
                    received += t.getQuantity();
                    break;
                case StockTransaction.TRANSFER_IN:
                    transferred += t.getQuantity();
                    break;
                default:
                    break;
            }
            if (recent.size() < 14) {
                Medicine m = medicineById.get(t.getMedicineId());
                recent.add(new LiveTxn(t.getOccurredAt(), t.getFacilityId(), facilityById.get(t.getFacilityId()).getName(),
                        t.getMedicineId(), m == null ? "?" : m.getName(), m == null ? "" : m.getUnit(), t.getTxnType(),
                        t.getQuantity(), t.getShortfall(), t.getBalanceAfter(), t.getReference()));
            }
        }
        List<MinuteBucket> perMinute = new ArrayList<>();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < minutes; i++) {
            perMinute.add(new MinuteBucket(windowStart.plusMinutes(i).format(hm), units[i], count[i]));
        }
        double fill = demanded == 0 ? 100.0 : Math.round(1000.0 * issued / demanded) / 10.0;
        return new Live(now, since, fs.get(0).getDistrict(), feedEnabled, feedSpeed, issued, demanded, issues, shorts,
                received, transferred, fill, perMinute, recent);
    }
}
