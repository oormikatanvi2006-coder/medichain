package com.medichain.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An item from the essential medicines list (names follow India's NLEM 2022). */
@Entity
@Table(name = "medicine")
public class Medicine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String category;
    private String unit;
    /** Comma-separated search keywords, e.g. "paracetamol,pcm,crocin". */
    private String aliases;
    /** Seasonal disease that drives demand (DENGUE, DIARRHOEA, ARI, MALARIA) or null. */
    private String linkedDisease;
    private int leadTimeDays;
    private double unitCost;
    /** Typical daily demand at a PHC; only used to simulate data. */
    private double baseDailyDemand;

    public Medicine() {
    }

    public Medicine(String code, String name, String category, String unit, String aliases,
                    String linkedDisease, int leadTimeDays, double unitCost, double baseDailyDemand) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.aliases = aliases;
        this.linkedDisease = linkedDisease;
        this.leadTimeDays = leadTimeDays;
        this.unitCost = unitCost;
        this.baseDailyDemand = baseDailyDemand;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    public String getLinkedDisease() { return linkedDisease; }
    public void setLinkedDisease(String linkedDisease) { this.linkedDisease = linkedDisease; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }
    public double getBaseDailyDemand() { return baseDailyDemand; }
    public void setBaseDailyDemand(double baseDailyDemand) { this.baseDailyDemand = baseDailyDemand; }
}
