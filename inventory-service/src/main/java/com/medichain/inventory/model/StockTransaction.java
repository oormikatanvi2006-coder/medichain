package com.medichain.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One line of the stock ledger: an issue to patients, a receipt, or one side of a transfer. */
@Entity
@Table(name = "stock_transaction")
public class StockTransaction {
    public static final String ISSUE = "ISSUE";
    public static final String RECEIPT = "RECEIPT";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TRANSFER_IN = "TRANSFER_IN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime occurredAt;
    private Long facilityId;
    private Long medicineId;
    private String txnType;
    private int quantity;
    /** Units asked for but not supplied because stock ran out (issues only). */
    private int shortfall;
    /** Usable stock on hand after this entry. */
    private int balanceAfter;
    private String reference;

    public StockTransaction() {
    }

    public StockTransaction(LocalDateTime occurredAt, Long facilityId, Long medicineId, String txnType, int quantity,
                            int shortfall, int balanceAfter, String reference) {
        this.occurredAt = occurredAt;
        this.facilityId = facilityId;
        this.medicineId = medicineId;
        this.txnType = txnType;
        this.quantity = quantity;
        this.shortfall = shortfall;
        this.balanceAfter = balanceAfter;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getShortfall() { return shortfall; }
    public void setShortfall(int shortfall) { this.shortfall = shortfall; }
    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
