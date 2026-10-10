package com.medichain.forecast.service;

import com.medichain.forecast.client.InventoryClient;
import com.medichain.forecast.client.InventoryDtos.TransferRequest;
import com.medichain.forecast.engine.AnalysisEngine;
import com.medichain.forecast.engine.Results.Transfer;
import com.medichain.forecast.model.Recommendation;
import com.medichain.forecast.repo.RecommendationRepository;
import com.medichain.forecast.web.ApiModels.TransferView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Stores recommendations and tracks their outcome (proposed, executed, rejected, superseded). */
@Service
public class RecommendationService {
    public static final int REJECTION_HOLD_HOURS = 24;

    private final RecommendationRepository repo;
    private final InventoryClient inventory;

    public RecommendationService(RecommendationRepository repo, InventoryClient inventory) {
        this.repo = repo;
        this.inventory = inventory;
    }

    /**
     * Saves the transfers of the latest analysis. An open recommendation for the same route and medicine is
     * updated in place; open ones the new analysis no longer supports are marked superseded.
     */
    public List<TransferView> sync(String district, Set<Long> analysedMedicineIds, List<Transfer> transfers,
                                   String queryText) {
        Map<String, Recommendation> open = new HashMap<>();
        for (Recommendation r : repo.findByDistrictIgnoreCaseAndStatus(district, Recommendation.PROPOSED)) {
            open.put(key(r.getMedicineId(), r.getFromFacilityId(), r.getToFacilityId()), r);
        }
        // A route the operator rejected in the last 24 hours is not proposed again.
        Set<String> recentlyRejected = new HashSet<>();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(REJECTION_HOLD_HOURS);
        for (Recommendation r : repo.findByDistrictIgnoreCaseAndStatus(district, Recommendation.REJECTED)) {
            if (r.getDecidedAt() != null && r.getDecidedAt().isAfter(cutoff)) {
                recentlyRejected.add(key(r.getMedicineId(), r.getFromFacilityId(), r.getToFacilityId()));
            }
        }
        List<TransferView> views = new ArrayList<>();
        for (Transfer t : transfers) {
            if (recentlyRejected.contains(key(t.medicineId(), t.fromFacilityId(), t.toFacilityId()))) {
                continue;
            }
            Recommendation r = open.remove(key(t.medicineId(), t.fromFacilityId(), t.toFacilityId()));
            if (r == null) {
                r = new Recommendation();
                r.setCreatedAt(LocalDateTime.now());
                r.setStatus(Recommendation.PROPOSED);
            }
            r.setDistrict(district);
            r.setMedicineId(t.medicineId());
            r.setMedicineName(t.medicineName());
            r.setUnit(t.unit());
            r.setFromFacilityId(t.fromFacilityId());
            r.setFromCode(t.fromCode());
            r.setFromName(t.fromName());
            r.setToFacilityId(t.toFacilityId());
            r.setToCode(t.toCode());
            r.setToName(t.toName());
            r.setQuantity(t.quantity());
            r.setFromStockBefore(t.fromStockBefore());
            r.setFromStockAfter(t.fromStockAfter());
            r.setToStockBefore(t.toStockBefore());
            r.setToStockAfter(t.toStockAfter());
            r.setDistanceKm(t.distanceKm());
            r.setReceiverDaysBefore(t.receiverDaysBefore());
            r.setReceiverDaysAfter(t.receiverDaysAfter());
            r.setDonorDaysBefore(t.donorDaysBefore());
            r.setDonorDaysAfter(t.donorDaysAfter());
            r.setUrgency(t.urgency());
            r.setRationale(t.rationale());
            r.setKeyFactors(String.join("\n", t.keyFactors()));
            r.setBatches(String.join("\n", t.batches()));
            r.setQueryText(queryText == null ? null : queryText.substring(0, Math.min(500, queryText.length())));
            views.add(TransferView.of(repo.save(r)));
        }
        for (Recommendation stale : open.values()) {
            if (analysedMedicineIds.contains(stale.getMedicineId())) {
                stale.setStatus(Recommendation.SUPERSEDED);
                stale.setDecidedAt(LocalDateTime.now());
                repo.save(stale);
            }
        }
        return views;
    }

    public List<TransferView> list() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(TransferView::of).toList();
    }

    /** Approves a transfer and executes it in inventory-service, which moves the batches. */
    public TransferView accept(Long id) {
        Recommendation r = open(id);
        Map<String, Object> result = inventory.transfer(new TransferRequest(r.getFromFacilityId(),
                r.getToFacilityId(), r.getMedicineId(), r.getQuantity(), AnalysisEngine.MIN_SHELF_LIFE_DAYS,
                "MediChain recommendation #" + r.getId()));
        Object transferId = result == null ? null : result.get("id");
        if (transferId instanceof Number) {
            r.setInventoryTransferId(((Number) transferId).longValue());
        }
        r.setStatus(Recommendation.EXECUTED);
        r.setDecidedAt(LocalDateTime.now());
        return TransferView.of(repo.save(r));
    }

    public TransferView reject(Long id) {
        Recommendation r = open(id);
        r.setStatus(Recommendation.REJECTED);
        r.setDecidedAt(LocalDateTime.now());
        return TransferView.of(repo.save(r));
    }

    private Recommendation open(Long id) {
        Recommendation r = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (!Recommendation.PROPOSED.equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recommendation is already " + r.getStatus());
        }
        return r;
    }

    private static String key(Long medicineId, Long from, Long to) {
        return medicineId + ":" + from + ":" + to;
    }
}
