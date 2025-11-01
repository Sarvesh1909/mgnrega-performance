package com.mgnrega.backend.repository;

import com.mgnrega.backend.entity.PerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, Long> {
    List<PerformanceRecord> findByStateNameAndDistrictNameOrderByFinYearDescMonthDesc(
        String stateName, String districtName);

    List<PerformanceRecord> findByStateNameAndDistrictNameAndFinYearOrderByMonthDesc(
        String stateName, String districtName, String finYear);

    @Query("SELECT p FROM PerformanceRecord p WHERE p.stateName = :stateName AND p.districtName = :districtName ORDER BY p.finYear DESC, p.month DESC")
    List<PerformanceRecord> findRecentByDistrict(@Param("stateName") String stateName, 
                                                   @Param("districtName") String districtName);

    @Query("SELECT p FROM PerformanceRecord p WHERE p.stateName = :stateName ORDER BY p.finYear DESC, p.month DESC")
    List<PerformanceRecord> findRecentByState(@Param("stateName") String stateName);

    @Query("SELECT AVG(p.persondaysGenerated) FROM PerformanceRecord p WHERE p.stateName = :stateName AND p.finYear = :finYear AND p.month = :month")
    Double findStateAveragePersondays(@Param("stateName") String stateName, 
                                      @Param("finYear") String finYear, 
                                      @Param("month") String month);

    @Query("SELECT AVG(p.householdsWorked) FROM PerformanceRecord p WHERE p.stateName = :stateName AND p.finYear = :finYear AND p.month = :month")
    Double findStateAverageHouseholds(@Param("stateName") String stateName, 
                                      @Param("finYear") String finYear, 
                                      @Param("month") String month);
}

