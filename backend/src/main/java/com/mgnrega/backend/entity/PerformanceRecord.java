package com.mgnrega.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_records", indexes = {
    @Index(name = "idx_state_district", columnList = "state_name,district_name"),
    @Index(name = "idx_year_month", columnList = "fin_year,month"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class PerformanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fin_year")
    private String finYear;

    @Column(name = "month")
    private String month;

    @Column(name = "state_name")
    private String stateName;

    @Column(name = "district_name")
    private String districtName;

    @Column(name = "households_worked")
    private Long householdsWorked;

    @Column(name = "persondays_generated")
    private Long persondaysGenerated;

    @Column(name = "women_persondays_percent")
    private Double womenPersondaysPercent;

    @Column(name = "no_of_ongoing_works")
    private Integer noOfOngoingWorks;

    @Column(name = "no_of_completed_works")
    private Integer noOfCompletedWorks;

    @Column(name = "avg_wage_rate")
    private Double avgWageRate;

    @Column(name = "total_wages")
    private Double totalWages;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFinYear() { return finYear; }
    public void setFinYear(String finYear) { this.finYear = finYear; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }

    public String getDistrictName() { return districtName; }
    public void setDistrictName(String districtName) { this.districtName = districtName; }

    public Long getHouseholdsWorked() { return householdsWorked; }
    public void setHouseholdsWorked(Long householdsWorked) { this.householdsWorked = householdsWorked; }

    public Long getPersondaysGenerated() { return persondaysGenerated; }
    public void setPersondaysGenerated(Long persondaysGenerated) { this.persondaysGenerated = persondaysGenerated; }

    public Double getWomenPersondaysPercent() { return womenPersondaysPercent; }
    public void setWomenPersondaysPercent(Double womenPersondaysPercent) { this.womenPersondaysPercent = womenPersondaysPercent; }

    public Integer getNoOfOngoingWorks() { return noOfOngoingWorks; }
    public void setNoOfOngoingWorks(Integer noOfOngoingWorks) { this.noOfOngoingWorks = noOfOngoingWorks; }

    public Integer getNoOfCompletedWorks() { return noOfCompletedWorks; }
    public void setNoOfCompletedWorks(Integer noOfCompletedWorks) { this.noOfCompletedWorks = noOfCompletedWorks; }

    public Double getAvgWageRate() { return avgWageRate; }
    public void setAvgWageRate(Double avgWageRate) { this.avgWageRate = avgWageRate; }

    public Double getTotalWages() { return totalWages; }
    public void setTotalWages(Double totalWages) { this.totalWages = totalWages; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

