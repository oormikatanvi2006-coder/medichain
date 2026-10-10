package com.medichain.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Daily reported cases of a seasonal disease in a district (disease surveillance feed). */
@Entity
@Table(name = "case_trend")
public class CaseTrend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String district;
    private String disease;
    private LocalDate reportDate;
    private int cases;

    public CaseTrend() {
    }

    public CaseTrend(String district, String disease, LocalDate reportDate, int cases) {
        this.district = district;
        this.disease = disease;
        this.reportDate = reportDate;
        this.cases = cases;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public int getCases() { return cases; }
    public void setCases(int cases) { this.cases = cases; }
}
