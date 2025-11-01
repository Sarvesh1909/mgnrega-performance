package com.mgnrega.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgnrega.backend.entity.PerformanceRecord;
import com.mgnrega.backend.repository.PerformanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comparatives")
@CrossOrigin(origins = "*")
public class ComparativesController {
    private static final Logger logger = LoggerFactory.getLogger(ComparativesController.class);
    private final PerformanceRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ComparativesController(PerformanceRecordRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/state-average")
    public ResponseEntity<String> getStateAverage(@RequestParam String state,
                                                    @RequestParam(required = false) String district,
                                                    @RequestParam(required = false) String finYear,
                                                    @RequestParam(required = false) String month) {
        try {
            // First try exact match
            List<PerformanceRecord> stateRecords = repository.findRecentByState(state);
            
            // If exact match not found, try case-insensitive search
            if (stateRecords.isEmpty()) {
                logger.info("No exact match for state '{}', trying case-insensitive search...", state);
                List<PerformanceRecord> allRecords = repository.findAll();
                stateRecords = allRecords.stream()
                    .filter(r -> r.getStateName() != null && 
                        r.getStateName().equalsIgnoreCase(state))
                    .sorted((a, b) -> {
                        int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                            ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                        if (yearComp != 0) return yearComp;
                        return (b.getMonth() != null && a.getMonth() != null) 
                            ? b.getMonth().compareTo(a.getMonth()) : 0;
                    })
                    .collect(Collectors.toList());
                
                if (!stateRecords.isEmpty()) {
                    logger.info("✅ Found {} records with case-insensitive match for state '{}'", stateRecords.size(), state);
                    logger.info("Actual state name in DB: '{}'", stateRecords.get(0).getStateName());
                } else {
                    // Try partial match (contains)
                    logger.info("No case-insensitive match, trying partial match...");
                    stateRecords = allRecords.stream()
                        .filter(r -> r.getStateName() != null && 
                            (r.getStateName().toLowerCase().contains(state.toLowerCase()) ||
                             state.toLowerCase().contains(r.getStateName().toLowerCase())))
                        .sorted((a, b) -> {
                            int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                                ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                            if (yearComp != 0) return yearComp;
                            return (b.getMonth() != null && a.getMonth() != null) 
                                ? b.getMonth().compareTo(a.getMonth()) : 0;
                        })
                        .collect(Collectors.toList());
                    
                    if (!stateRecords.isEmpty()) {
                        logger.info("✅ Found {} records with partial match for state '{}'", stateRecords.size(), state);
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            
            if (stateRecords.isEmpty()) {
                // Check what states are actually available in database
                List<PerformanceRecord> allRecords = repository.findAll();
                Set<String> availableStates = allRecords.stream()
                    .filter(r -> r.getStateName() != null)
                    .map(r -> r.getStateName())
                    .collect(Collectors.toSet());
                
                logger.warn("No state records found for: {}. Available states in DB: {}", state, availableStates);
                response.put("error", "No data available for state: " + state + ". Please fetch performance data first by clicking 'View Performance'.");
                if (!availableStates.isEmpty()) {
                    response.put("availableStates", new ArrayList<>(availableStates));
                    response.put("hint", "Available states in database: " + String.join(", ", availableStates));
                }
                return ResponseEntity.ok(objectMapper.writeValueAsString(response));
            }

            // Get latest month/year from records
            PerformanceRecord latest = stateRecords.get(0);
            String year = finYear != null ? finYear : latest.getFinYear();
            String mon = month != null ? month : latest.getMonth();

            // Calculate state averages for the specified period
            // Use the actual state name from records (case-insensitive match)
            String actualStateName = stateRecords.get(0).getStateName();
            Double avgPersondays = repository.findStateAveragePersondays(actualStateName, year, mon);
            Double avgHouseholds = repository.findStateAverageHouseholds(actualStateName, year, mon);
            
            // If averages are null, calculate manually from available records
            if (avgPersondays == null || avgHouseholds == null) {
                logger.info("Calculating average manually from {} records for state '{}', year '{}', month '{}'", 
                    stateRecords.size(), actualStateName, year, mon);
                
                List<PerformanceRecord> recordsForPeriod = stateRecords.stream()
                    .filter(r -> year.equals(r.getFinYear()) && mon.equals(r.getMonth()))
                    .collect(Collectors.toList());
                
                if (!recordsForPeriod.isEmpty()) {
                    avgPersondays = recordsForPeriod.stream()
                        .filter(r -> r.getPersondaysGenerated() != null)
                        .mapToDouble(r -> r.getPersondaysGenerated().doubleValue())
                        .average()
                        .orElse(0.0);
                    
                    avgHouseholds = recordsForPeriod.stream()
                        .filter(r -> r.getHouseholdsWorked() != null)
                        .mapToLong(r -> r.getHouseholdsWorked())
                        .average()
                        .orElse(0.0);
                    
                    logger.info("✅ Calculated average from {} records: persondays={}, households={}", 
                        recordsForPeriod.size(), avgPersondays, avgHouseholds);
                } else {
                    // If no records for exact period, use all available records for the state
                    logger.info("No records for exact period '{}'/'{}', calculating from all available records for state", year, mon);
                    if (!stateRecords.isEmpty()) {
                        List<PerformanceRecord> recordsWithData = stateRecords.stream()
                            .filter(r -> r.getPersondaysGenerated() != null || r.getHouseholdsWorked() != null)
                            .collect(Collectors.toList());
                        
                        if (!recordsWithData.isEmpty()) {
                            avgPersondays = recordsWithData.stream()
                                .filter(r -> r.getPersondaysGenerated() != null)
                                .mapToDouble(r -> r.getPersondaysGenerated().doubleValue())
                                .average()
                                .orElse(0.0);
                            
                            avgHouseholds = recordsWithData.stream()
                                .filter(r -> r.getHouseholdsWorked() != null)
                                .mapToLong(r -> r.getHouseholdsWorked())
                                .average()
                                .orElse(0.0);
                            
                            logger.info("✅ Calculated average from {} records: persondays={}, households={}", 
                                recordsWithData.size(), avgPersondays, avgHouseholds);
                        }
                    }
                }
            }
            
            // Ensure we have values (default to 0 if still null)
            if (avgPersondays == null) avgPersondays = 0.0;
            if (avgHouseholds == null) avgHouseholds = 0.0;

            // Get district data if specified
            Double districtPersondays = null;
            Long districtHouseholds = null;
            
            if (district != null) {
                // Try exact month/year match first
                List<PerformanceRecord> districtRecords = stateRecords.stream()
                    .filter(r -> district != null && r.getDistrictName() != null && 
                        district.equalsIgnoreCase(r.getDistrictName()))
                    .filter(r -> year.equals(r.getFinYear()) && mon.equals(r.getMonth()))
                    .collect(Collectors.toList());
                
                // If no exact match, try to find latest record for this district
                if (districtRecords.isEmpty()) {
                    logger.info("No exact match for district '{}' in period {}/{}, trying latest available...", district, mon, year);
                    districtRecords = stateRecords.stream()
                        .filter(r -> district != null && r.getDistrictName() != null && 
                            district.equalsIgnoreCase(r.getDistrictName()))
                        .sorted((a, b) -> {
                            int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                                ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                            if (yearComp != 0) return yearComp;
                            return (b.getMonth() != null && a.getMonth() != null) 
                                ? b.getMonth().compareTo(a.getMonth()) : 0;
                        })
                        .limit(1)
                        .collect(Collectors.toList());
                    
                    if (!districtRecords.isEmpty()) {
                        PerformanceRecord foundRecord = districtRecords.get(0);
                        logger.info("✅ Found latest record for district '{}': {}/{}", 
                            district, foundRecord.getMonth(), foundRecord.getFinYear());
                        // Use the year/month from the found record for comparison
                        // But keep original year/mon for state average calculation
                    } else {
                        // List available districts for this state
                        Set<String> availableDistricts = stateRecords.stream()
                            .filter(r -> r.getDistrictName() != null)
                            .map(r -> r.getDistrictName())
                            .collect(Collectors.toSet());
                        logger.warn("District '{}' not found in state '{}'. Available districts: {}", 
                            district, actualStateName, availableDistricts);
                    }
                }
                
                if (!districtRecords.isEmpty()) {
                    PerformanceRecord distRec = districtRecords.get(0);
                    districtPersondays = distRec.getPersondaysGenerated() != null ? 
                        distRec.getPersondaysGenerated().doubleValue() : null;
                    districtHouseholds = distRec.getHouseholdsWorked();
                    logger.info("District '{}' data: persondays={}, households={}", district, districtPersondays, districtHouseholds);
                } else {
                    logger.warn("No records found for district '{}' in state '{}'", district, actualStateName);
                }
            }

            response.put("state", actualStateName); // Use actual state name found
            response.put("requestedState", state); // Keep original for reference
            response.put("year", year);
            response.put("month", mon);
            response.put("stateAveragePersondays", avgPersondays != null ? Math.round(avgPersondays) : 0);
            response.put("stateAverageHouseholds", avgHouseholds != null ? Math.round(avgHouseholds) : 0);
            response.put("recordsUsed", stateRecords.size()); // Debug info
            
            if (district != null) {
                response.put("district", district);
                response.put("districtPersondays", districtPersondays != null ? Math.round(districtPersondays) : null);
                response.put("districtHouseholds", districtHouseholds);
                
                if (avgPersondays != null && avgPersondays > 0 && districtPersondays != null && districtPersondays > 0) {
                    double percentage = ((districtPersondays - avgPersondays) / avgPersondays) * 100;
                    response.put("persondaysDifferencePercent", Math.round(percentage * 100.0) / 100.0);
                    response.put("aboveStateAverage", districtPersondays > avgPersondays);
                } else {
                    // If one is missing, set difference to 0
                    response.put("persondaysDifferencePercent", 0.0);
                    response.put("aboveStateAverage", false);
                    if (districtPersondays == null || districtPersondays == 0) {
                        response.put("districtDataMissing", true);
                        response.put("message", "District data not available for comparison period");
                        // Include available districts for this state
                        Set<String> availableDistricts = stateRecords.stream()
                            .filter(r -> r.getDistrictName() != null)
                            .map(r -> r.getDistrictName())
                            .collect(Collectors.toSet());
                        response.put("availableDistricts", new ArrayList<>(availableDistricts));
                    }
                }
            }

            return ResponseEntity.ok(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logger.error("Error calculating state average: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/district-comparison")
    public ResponseEntity<String> compareDistricts(@RequestParam String state,
                                                     @RequestParam String district1,
                                                     @RequestParam String district2,
                                                     @RequestParam(required = false) String finYear,
                                                     @RequestParam(required = false) String month) {
        try {
            List<PerformanceRecord> allRecords = repository.findRecentByState(state);
            
            // If exact match not found, try case-insensitive search
            if (allRecords.isEmpty()) {
                logger.info("No exact match for state '{}', trying case-insensitive search...", state);
                List<PerformanceRecord> allRecordsInDB = repository.findAll();
                allRecords = allRecordsInDB.stream()
                    .filter(r -> r.getStateName() != null && 
                        r.getStateName().equalsIgnoreCase(state))
                    .sorted((a, b) -> {
                        int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                            ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                        if (yearComp != 0) return yearComp;
                        return (b.getMonth() != null && a.getMonth() != null) 
                            ? b.getMonth().compareTo(a.getMonth()) : 0;
                    })
                    .collect(Collectors.toList());
                
                if (!allRecords.isEmpty()) {
                    logger.info("Found {} records with case-insensitive match for state '{}'", allRecords.size(), state);
                }
            }
            
            if (allRecords.isEmpty()) {
                // Check what states are actually available
                List<PerformanceRecord> allRecordsInDB = repository.findAll();
                Set<String> availableStates = allRecordsInDB.stream()
                    .filter(r -> r.getStateName() != null)
                    .map(r -> r.getStateName())
                    .collect(Collectors.toSet());
                
                logger.warn("No records found for state: {}. Available states in DB: {}", state, availableStates);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "No data available for state: " + state + ". Please fetch performance data first.");
                if (!availableStates.isEmpty()) {
                    errorResponse.put("availableStates", new ArrayList<>(availableStates));
                    errorResponse.put("hint", "Available states in database: " + String.join(", ", availableStates));
                }
                return ResponseEntity.ok(objectMapper.writeValueAsString(errorResponse));
            }

            PerformanceRecord latest = allRecords.get(0);
            String year = finYear != null ? finYear : latest.getFinYear();
            String mon = month != null ? month : latest.getMonth();

            // Find districts with case-insensitive matching and flexible month/year matching
            PerformanceRecord d1 = allRecords.stream()
                .filter(r -> district1 != null && r.getDistrictName() != null && 
                    district1.equalsIgnoreCase(r.getDistrictName()))
                .filter(r -> {
                    // Try exact match first
                    if (year.equals(r.getFinYear()) && mon.equals(r.getMonth())) return true;
                    // If no exact match, try latest month/year for this district
                    return year == null || mon == null || 
                        (r.getFinYear() != null && r.getMonth() != null);
                })
                .sorted((a, b) -> {
                    // Prefer exact month/year match
                    boolean aExact = year.equals(a.getFinYear()) && mon.equals(a.getMonth());
                    boolean bExact = year.equals(b.getFinYear()) && mon.equals(b.getMonth());
                    if (aExact && !bExact) return -1;
                    if (!aExact && bExact) return 1;
                    // Then by year/month descending
                    int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                        ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                    if (yearComp != 0) return yearComp;
                    return (b.getMonth() != null && a.getMonth() != null) 
                        ? b.getMonth().compareTo(a.getMonth()) : 0;
                })
                .findFirst()
                .orElse(null);

            PerformanceRecord d2 = allRecords.stream()
                .filter(r -> district2 != null && r.getDistrictName() != null && 
                    district2.equalsIgnoreCase(r.getDistrictName()))
                .filter(r -> {
                    if (year.equals(r.getFinYear()) && mon.equals(r.getMonth())) return true;
                    return year == null || mon == null || 
                        (r.getFinYear() != null && r.getMonth() != null);
                })
                .sorted((a, b) -> {
                    boolean aExact = year.equals(a.getFinYear()) && mon.equals(a.getMonth());
                    boolean bExact = year.equals(b.getFinYear()) && mon.equals(b.getMonth());
                    if (aExact && !bExact) return -1;
                    if (!aExact && bExact) return 1;
                    int yearComp = (b.getFinYear() != null && a.getFinYear() != null) 
                        ? b.getFinYear().compareTo(a.getFinYear()) : 0;
                    if (yearComp != 0) return yearComp;
                    return (b.getMonth() != null && a.getMonth() != null) 
                        ? b.getMonth().compareTo(a.getMonth()) : 0;
                })
                .findFirst()
                .orElse(null);

            Map<String, Object> response = new HashMap<>();
            response.put("year", year);
            response.put("month", mon);
            response.put("district1", createDistrictData(district1, d1));
            response.put("district2", createDistrictData(district2, d2));

            if (d1 != null && d2 != null && d1.getPersondaysGenerated() != null && d2.getPersondaysGenerated() != null) {
                long diff = d1.getPersondaysGenerated() - d2.getPersondaysGenerated();
                response.put("differencePersondays", diff);
                response.put("betterDistrict", diff > 0 ? district1 : district2);
            }

            return ResponseEntity.ok(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logger.error("Error comparing districts: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Map<String, Object> createDistrictData(String name, PerformanceRecord record) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        if (record != null) {
            data.put("persondaysGenerated", record.getPersondaysGenerated());
            data.put("householdsWorked", record.getHouseholdsWorked());
            data.put("womenPersondaysPercent", record.getWomenPersondaysPercent());
            data.put("noOfOngoingWorks", record.getNoOfOngoingWorks());
            data.put("noOfCompletedWorks", record.getNoOfCompletedWorks());
            data.put("avgWageRate", record.getAvgWageRate());
        } else {
            data.put("error", "No data available");
        }
        return data;
    }
}

