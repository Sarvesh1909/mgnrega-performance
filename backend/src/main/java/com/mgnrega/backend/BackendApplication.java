package com.mgnrega.backend;

import com.mgnrega.backend.repository.PerformanceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import com.mgnrega.backend.entity.PerformanceRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @RestController
    @RequestMapping("/api/health")
    public static class HealthController {
        @GetMapping
        public Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }

    @RestController
    @RequestMapping("/api/debug")
    public static class DebugController {
        
        @Autowired(required = false)
        private PerformanceRecordRepository performanceRecordRepository;
        
        @GetMapping("/clear-null-records")
        public ResponseEntity<String> clearNullRecords() {
            try {
                if (performanceRecordRepository == null) {
                    return ResponseEntity.status(500).body("{\"error\":\"Repository not available\"}");
                }
                
                List<PerformanceRecord> allRecords = performanceRecordRepository.findAll();
                List<PerformanceRecord> nullRecords = allRecords.stream()
                    .filter(r -> r.getPersondaysGenerated() == null && 
                               r.getHouseholdsWorked() == null && 
                               r.getAvgWageRate() == null && 
                               r.getTotalWages() == null)
                    .collect(java.util.stream.Collectors.toList());
                
                if (!nullRecords.isEmpty()) {
                    performanceRecordRepository.deleteAll(nullRecords);
                    return ResponseEntity.ok("{\"message\":\"Deleted " + nullRecords.size() + " records with null data\",\"deletedCount\":" + nullRecords.size() + "}");
                } else {
                    return ResponseEntity.ok("{\"message\":\"No null records found\",\"deletedCount\":0}");
                }
            } catch (Exception e) {
                return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        @GetMapping("/states")
        public Map<String, Object> getStatesInDatabase() {
            Map<String, Object> result = new HashMap<>();
            try {
                if (performanceRecordRepository == null) {
                    result.put("error", "Repository not available");
                    return result;
                }
                
                // Get all unique states and districts from database
                List<com.mgnrega.backend.entity.PerformanceRecord> allRecords = performanceRecordRepository.findAll();
                java.util.Set<String> states = new java.util.HashSet<>();
                java.util.Map<String, java.util.Set<String>> stateDistricts = new java.util.HashMap<>();
                
                for (com.mgnrega.backend.entity.PerformanceRecord rec : allRecords) {
                    if (rec.getStateName() != null) {
                        states.add(rec.getStateName());
                        stateDistricts.computeIfAbsent(rec.getStateName(), k -> new java.util.HashSet<>())
                            .add(rec.getDistrictName());
                    }
                }
                
                result.put("totalRecords", allRecords.size());
                result.put("states", states);
                result.put("stateDistricts", stateDistricts);
                result.put("message", "Database contains " + states.size() + " states and " + allRecords.size() + " total records");
                
            } catch (Exception e) {
                result.put("error", e.getMessage());
            }
            return result;
        }
    }

    @RestController
    @RequestMapping("/api/db-check")
    public static class DatabaseCheckController {
        
        @Autowired(required = false)
        private JdbcTemplate jdbcTemplate;
        
        @Autowired(required = false)
        private PerformanceRecordRepository performanceRecordRepository;

        @GetMapping
        public Map<String, Object> checkDatabase() {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (jdbcTemplate == null) {
                    result.put("connected", false);
                    result.put("error", "Database connection not configured (JdbcTemplate is null)");
                    result.put("message", "Database is not available - check application.properties");
                    return result;
                }

                // Test connection with a simple query
                String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
                String dbUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
                Long recordCount = 0L;
                
                // Try to count records in performance_records table
                try {
                    recordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM performance_records", Long.class);
                } catch (Exception e) {
                    result.put("tableExists", false);
                    result.put("tableError", "Table 'performance_records' may not exist yet. It will be created on first save.");
                }
                
                result.put("connected", true);
                result.put("database", dbName);
                result.put("user", dbUser);
                result.put("recordCount", recordCount);
                result.put("message", "Database connection successful!");
                
                // Test repository if available
                if (performanceRecordRepository != null) {
                    try {
                        long repoCount = performanceRecordRepository.count();
                        result.put("repositoryCount", repoCount);
                        result.put("repositoryWorking", true);
                    } catch (Exception e) {
                        result.put("repositoryWorking", false);
                        result.put("repositoryError", e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                result.put("connected", false);
                result.put("error", e.getMessage());
                result.put("errorType", e.getClass().getSimpleName());
                result.put("message", "Database connection failed: " + e.getMessage());
                
                // Common error messages
                if (e.getMessage().contains("password") || e.getMessage().contains("authentication")) {
                    result.put("suggestion", "Check your database password in application.properties");
                } else if (e.getMessage().contains("Connection refused") || e.getMessage().contains("refused")) {
                    result.put("suggestion", "PostgreSQL service may not be running. Start PostgreSQL service.");
                } else if (e.getMessage().contains("database") && e.getMessage().contains("does not exist")) {
                    result.put("suggestion", "Database 'mgnrega' does not exist. Create it first: CREATE DATABASE mgnrega;");
                }
            }
            
            return result;
        }
    }
}
