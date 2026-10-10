package com.medichain.forecast.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A transfer recommendation and what the operator decided to do with it. */
@Entity
@Table(name = "recommendation")
public class Recommendation {
    public static final String PROPOSED = "PROPOSED";
    public static final String EXECUTED = "EXECUTED";
    public static final String REJECTED = "REJECTED";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String district;
    private Long medicineId;
    private String medicineName;
    private String unit;
    private Long fromFacilityId;
    private String fromCode;
    private String fromName;
    private Long toFacilityId;
    private String toCode;
    private String toName;
    private int quantity;
    private int fromStockBefore;
    private int fromStockAfter;
    private int toStockBefore;
    private int toStockAfter;
    private double distanceKm;
    private double receiverDaysBefore;
    private double receiverDaysAfter;
    private double donorDaysBefore;
    private double donorDaysAfter;
    /** Units that would have expired at the donor and are put to use by this transfer. */
    private int expirySavedUnits;
    private double expirySavedValue;
    /** Days of stock the receiver gains. */
    private double coverDaysGained;
    private String urgency;
    @Column(length = 1000)
    private String rationale;
    /** One factor per line. */
    @Column(length = 4000)
    private String keyFactors;
    /** One batch per line. */
    @Column(length = 2000)
    private String batches;
    @Column(length = 500)
    private String queryText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private Long inventoryTransferId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getFromFacilityId() { return fromFacilityId; }
    public void setFromFacilityId(Long fromFacilityId) { this.fromFacilityId = fromFacilityId; }
    public String getFromCode() { return fromCode; }
    public void setFromCode(String fromCode) { this.fromCode = fromCode; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public Long getToFacilityId() { return toFacilityId; }
    public void setToFacilityId(Long toFacilityId) { this.toFacilityId = toFacilityId; }
    public String getToCode() { return toCode; }
    public void setToCode(String toCode) { this.toCode = toCode; }
    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getFromStockBefore() { return fromStockBefore; }
    public void setFromStockBefore(int fromStockBefore) { this.fromStockBefore = fromStockBefore; }
    public int getFromStockAfter() { return fromStockAfter; }
    public void setFromStockAfter(int fromStockAfter) { this.fromStockAfter = fromStockAfter; }
    public int getToStockBefore() { return toStockBefore; }
    public void setToStockBefore(int toStockBefore) { this.toStockBefore = toStockBefore; }
    public int getToStockAfter() { return toStockAfter; }
    public void setToStockAfter(int toStockAfter) { this.toStockAfter = toStockAfter; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public double getReceiverDaysBefore() { return receiverDaysBefore; }
    public void setReceiverDaysBefore(double receiverDaysBefore) { this.receiverDaysBefore = receiverDaysBefore; }
    public double getReceiverDaysAfter() { return receiverDaysAfter; }
    public void setReceiverDaysAfter(double receiverDaysAfter) { this.receiverDaysAfter = receiverDaysAfter; }
    public double getDonorDaysBefore() { return donorDaysBefore; }
    public void setDonorDaysBefore(double donorDaysBefore) { this.donorDaysBefore = donorDaysBefore; }
    public double getDonorDaysAfter() { return donorDaysAfter; }
    public void setDonorDaysAfter(double donorDaysAfter) { this.donorDaysAfter = donorDaysAfter; }
    public int getExpirySavedUnits() { return expirySavedUnits; }
    public void setExpirySavedUnits(int expirySavedUnits) { this.expirySavedUnits = expirySavedUnits; }
    public double getExpirySavedValue() { return expirySavedValue; }
    public void setExpirySavedValue(double expirySavedValue) { this.expirySavedValue = expirySavedValue; }
    public double getCoverDaysGained() { return coverDaysGained; }
    public void setCoverDaysGained(double coverDaysGained) { this.coverDaysGained = coverDaysGained; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getKeyFactors() { return keyFactors; }
    public void setKeyFactors(String keyFactors) { this.keyFactors = keyFactors; }
    public String getBatches() { return batches; }
    public void setBatches(String batches) { this.batches = batches; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public Long getInventoryTransferId() { return inventoryTransferId; }
    public void setInventoryTransferId(Long inventoryTransferId) { this.inventoryTransferId = inventoryTransferId; }
}
