package com.medichain.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One day of demand and dispensing for a medicine at a facility. */
@Entity
@Table(name = "consumption_log")
public class ConsumptionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long facilityId;
    private Long medicineId;
    private LocalDate logDate;
    /** Units patients needed. */
    private int demanded;
    /** Units actually issued; lower than demanded on a stock-out day. */
    private int dispensed;

    public ConsumptionLog() {
    }

    public ConsumptionLog(Long facilityId, Long medicineId, LocalDate logDate, int demanded, int dispensed) {
        this.facilityId = facilityId;
        this.medicineId = medicineId;
        this.logDate = logDate;
        this.demanded = demanded;
        this.dispensed = dispensed;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }
    public int getDemanded() { return demanded; }
    public void setDemanded(int demanded) { this.demanded = demanded; }
    public int getDispensed() { return dispensed; }
    public void setDispensed(int dispensed) { this.dispensed = dispensed; }
}
