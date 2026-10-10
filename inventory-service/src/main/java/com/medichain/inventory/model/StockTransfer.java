package com.medichain.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A completed movement of stock between two facilities. */
@Entity
@Table(name = "stock_transfer")
public class StockTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long fromFacilityId;
    private Long toFacilityId;
    private Long medicineId;
    private int quantity;
    private LocalDateTime transferredAt;
    private String reference;

    public StockTransfer() {
    }

    public StockTransfer(Long fromFacilityId, Long toFacilityId, Long medicineId, int quantity,
                         LocalDateTime transferredAt, String reference) {
        this.fromFacilityId = fromFacilityId;
        this.toFacilityId = toFacilityId;
        this.medicineId = medicineId;
        this.quantity = quantity;
        this.transferredAt = transferredAt;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromFacilityId() { return fromFacilityId; }
    public void setFromFacilityId(Long fromFacilityId) { this.fromFacilityId = fromFacilityId; }
    public Long getToFacilityId() { return toFacilityId; }
    public void setToFacilityId(Long toFacilityId) { this.toFacilityId = toFacilityId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public LocalDateTime getTransferredAt() { return transferredAt; }
    public void setTransferredAt(LocalDateTime transferredAt) { this.transferredAt = transferredAt; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
