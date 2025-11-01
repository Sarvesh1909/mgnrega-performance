package com.mgnrega.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgnrega.backend.entity.PerformanceRecord;
import com.mgnrega.backend.service.DataGovClient;
import com.mgnrega.backend.service.PerformanceDataService;
import com.mgnrega.backend.service.RateLimiter;
import com.mgnrega.backend.service.SimpleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@CrossOrigin(origins = "*")
public class PerformanceController {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceController.class);
    private final DataGovClient client;
    private final PerformanceDataService dataService;
    private final RateLimiter rateLimiter;
    private final SimpleCache<String, String> cache;
    private final ObjectMapper objectMapper;
    private final String resourceId;
    private final boolean useDatabase;

    public PerformanceController(DataGovClient client,
                                 PerformanceDataService dataService,
                                 RateLimiter rateLimiter,
                                 @Value("${datagov.resourceId:ee03643a-ee4c-48c2-ac30-9f2ff26ab722}") String resourceId,
                                 @Value("${datagov.cacheTtlSeconds:900}") long ttlSeconds,
                                 @Value("${app.useDatabase:true}") boolean useDatabase) {
        this.client = client;
        this.dataService = dataService;
        this.rateLimiter = rateLimiter;
        this.resourceId = resourceId;
        this.useDatabase = useDatabase;
        this.cache = new SimpleCache<>(ttlSeconds * 1000);
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<String> getPerformance(@RequestParam(required = false) String state,
                                                   @RequestParam(required = false) String district,
                                                   @RequestParam(required = false) String month,
                                                   @RequestParam(required = false) String year,
                                                   @RequestParam(required = false, defaultValue = "12") String limit) {
        try {
            String cacheKey = (state == null ? "" : state) + "|" + (district == null ? "" : district) + "|" + (month == null ? "" : month) + "|" + (year == null ? "" : year) + "|" + limit;
            String cached = cache.get(cacheKey);
            if (cached != null) {
                logger.info("Returning cached data for key: {}", cacheKey);
                return ResponseEntity.ok(cached);
            }

            // Try database first if enabled
            if (useDatabase && state != null && district != null) {
                List<PerformanceRecord> dbRecords = dataService.getFromDatabase(state, district, Integer.parseInt(limit));
                if (!dbRecords.isEmpty()) {
                    logger.info("Returning {} records from database", dbRecords.size());
                    Map<String, Object> response = new HashMap<>();
                    response.put("records", dbRecords);
                    response.put("source", "database");
                    String jsonResponse = objectMapper.writeValueAsString(response);
                    cache.put(cacheKey, jsonResponse);
                    return ResponseEntity.ok(jsonResponse);
                }
            }

            // Rate limiting check
            if (!rateLimiter.allowRequest("datagov-api")) {
                logger.warn("Rate limit exceeded, returning database data if available");
                if (useDatabase && state != null && district != null) {
                    List<PerformanceRecord> dbRecords = dataService.getFromDatabase(state, district, Integer.parseInt(limit));
                    if (!dbRecords.isEmpty()) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("records", dbRecords);
                        response.put("source", "database");
                        response.put("note", "Rate limited - showing cached data");
                        return ResponseEntity.ok(objectMapper.writeValueAsString(response));
                    }
                }
                return ResponseEntity.status(429).body("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
            }

            // Fetch from API
            Map<String, String> q = new HashMap<>();
            q.put("limit", limit);
            if (state != null && !state.isBlank()) {
                q.put("filters[state_name]", state);
                logger.info("Filtering by state: {}", state);
            }
            if (district != null && !district.isBlank()) {
                q.put("filters[district_name]", district);
                logger.info("Filtering by district: {}", district);
            }
            if (month != null && !month.isBlank()) q.put("filters[month]", month);
            if (year != null && !year.isBlank()) q.put("filters[fin_year]", year);
            
            logger.info("API query parameters: {}", q);
            String result = client.fetchResourceJson(resourceId, q);
            
            // Check if API returned empty results - use more flexible matching
            boolean isEmptyResult = result != null && (
                result.contains("\"total\":0") || 
                result.contains("\"total\": 0") ||
                result.contains("\"count\":0") ||
                result.contains("\"count\": 0") ||
                (result.contains("\"records\"") && result.contains("[]"))
            );
            
            if (isEmptyResult && district != null && !district.isBlank() && state != null && !state.isBlank()) {
                logger.warn("API returned 0 records for district-specific query (state: '{}', district: '{}')", state, district);
                logger.info("Attempting fallback: Querying state-only to get all districts data...");
                
                // Try without district filter to get state data
                Map<String, String> stateOnlyQuery = new HashMap<>();
                stateOnlyQuery.put("limit", "100");
                stateOnlyQuery.put("filters[state_name]", state);
                
                try {
                    String stateResult = client.fetchResourceJson(resourceId, stateOnlyQuery);
                    
                    // Check if state query has data
                    boolean hasStateData = stateResult != null && 
                        !stateResult.contains("\"error\"") &&
                        !stateResult.contains("\"total\":0") &&
                        !stateResult.contains("\"total\": 0") &&
                        stateResult.contains("\"records\"") &&
                        !stateResult.contains("\"records\":[]");
                    
                    if (hasStateData) {
                        logger.info("✅ Success! Found data when querying state only (without district filter)");
                        logger.info("This suggests district name '{}' might not match exactly in API. Saving state-level data...", district);
                        
                        // Save the state data (multiple districts)
                        if (useDatabase) {
                            dataService.savePerformanceData(stateResult);
                            logger.info("✅ Saved state-level data to database. State comparisons will now work.");
                        }
                        
                        // Update result to use state data if we got it, otherwise keep original
                        // This way user gets data even if district filter didn't work
                        result = stateResult;
                    } else {
                        logger.warn("Even state-only query returned 0 records. Trying unfiltered query to check API...");
                        
                        // Last resort: Try querying without any filters to see what states exist
                        try {
                            Map<String, String> unfilteredQuery = new HashMap<>();
                            unfilteredQuery.put("limit", "100"); // Get more records to ensure we find data
                            String unfilteredResult = client.fetchResourceJson(resourceId, unfilteredQuery);
                            
                            // Check if unfiltered query returned data
                            boolean hasUnfilteredData = unfilteredResult != null && 
                                !unfilteredResult.contains("\"error\"") &&
                                unfilteredResult.contains("\"records\"") &&
                                !unfilteredResult.contains("\"records\":[]");
                            
                            // Also check if total/count indicates data exists
                            if (hasUnfilteredData) {
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper testMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    com.fasterxml.jackson.databind.JsonNode testRoot = testMapper.readTree(unfilteredResult);
                                    com.fasterxml.jackson.databind.JsonNode testRecords = testRoot.get("records");
                                    if (testRecords != null && testRecords.isArray() && testRecords.size() > 0) {
                                        hasUnfilteredData = true;
                                    } else {
                                        hasUnfilteredData = false;
                                    }
                                } catch (Exception e) {
                                    // If parsing fails, rely on string check
                                }
                            }
                            
                            if (hasUnfilteredData) {
                                logger.info("✅ API is working! Found data without filters.");
                                logger.info("This suggests state name '{}' might not match exactly. Saving unfiltered data...", state);
                                
                                // Save unfiltered data to see what states/districts are available
                                if (useDatabase) {
                                    dataService.savePerformanceData(unfilteredResult);
                                    logger.info("✅ Saved sample data. This will help identify correct state/district names.");
                                    
                                    // Log what states we found
                                    try {
                                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(unfilteredResult);
                                        com.fasterxml.jackson.databind.JsonNode records = root.get("records");
                                        if (records != null && records.isArray() && records.size() > 0) {
                                            java.util.Set<String> foundStates = new java.util.HashSet<>();
                                            java.util.Set<String> foundDistricts = new java.util.HashSet<>();
                                            for (com.fasterxml.jackson.databind.JsonNode rec : records) {
                                                if (rec.get("state_name") != null) foundStates.add(rec.get("state_name").asText());
                                                if (rec.get("district_name") != null) foundDistricts.add(rec.get("district_name").asText());
                                            }
                                            logger.info("📋 Found {} states in sample: {}", foundStates.size(), foundStates);
                                            logger.info("📋 Found {} districts in sample: {}", foundDistricts.size(), 
                                                foundDistricts.size() > 10 ? foundDistricts.stream().limit(10).toList() + "..." : foundDistricts);
                                            logger.info("💡 Tip: Check these names to see if '{}' matches any state name", state);
                                        }
                                    } catch (Exception e) {
                                        logger.debug("Could not parse sample data for logging: {}", e.getMessage());
                                    }
                                }
                                
                                // Filter unfiltered data to show only requested state/district if possible
                                // But if no state match, show all data so user at least sees something
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(unfilteredResult);
                                    com.fasterxml.jackson.databind.JsonNode records = root.get("records");
                                    
                                    if (records != null && records.isArray()) {
                                        // Try to filter for the requested state (case-insensitive)
                                        java.util.List<com.fasterxml.jackson.databind.JsonNode> filtered = new java.util.ArrayList<>();
                                        boolean foundStateMatch = false;
                                        
                                        for (com.fasterxml.jackson.databind.JsonNode rec : records) {
                                            if (rec.get("state_name") != null) {
                                                String recState = rec.get("state_name").asText();
                                                if (recState != null && recState.equalsIgnoreCase(state)) {
                                                    filtered.add(rec);
                                                    foundStateMatch = true;
                                                }
                                            }
                                        }
                                        
                                        if (foundStateMatch && !filtered.isEmpty()) {
                                            // Create filtered response with only matching state
                                            root = mapper.createObjectNode();
                                            ((com.fasterxml.jackson.databind.node.ObjectNode) root).putArray("records").addAll(filtered);
                                            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("total", filtered.size());
                                            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("count", filtered.size());
                                            unfilteredResult = mapper.writeValueAsString(root);
                                            logger.info("✅ Filtered unfiltered data to show only '{}' records ({} found)", state, filtered.size());
                                        } else {
                                            logger.info("No records found for state '{}' in unfiltered data. Showing all available data.", state);
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.debug("Could not filter unfiltered data: {}", e.getMessage());
                                }
                                
                                // Use unfiltered data so user sees something
                                result = unfilteredResult;
                            } else {
                                logger.error("❌ API returned no data even without filters. API might be empty or down.");
                                logger.warn("Possible issues:");
                                logger.warn("1. State name '{}' doesn't match API exactly", state);
                                logger.warn("2. API might use different field names (e.g., 'State' instead of 'state_name')");
                                logger.warn("3. API might be down or rate-limited");
                                logger.warn("4. No data available in API for this resource");
                            }
                        } catch (Exception e) {
                            logger.error("Error in unfiltered query: {}", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in fallback state query: {}", e.getMessage());
                }
            }
            
            // Save to database if enabled (only if not already saved in fallback)
            // Check if result is empty - if fallback already saved state data, don't save empty result
            boolean isResultEmpty = result != null && (
                result.contains("\"total\":0") || 
                result.contains("\"total\": 0") ||
                (result.contains("\"records\"") && result.contains("[]"))
            );
            
            if (useDatabase) {
                if (result.contains("\"error\"")) {
                    logger.warn("Skipping save due to error in API response. Response preview: {}", 
                        result.substring(0, Math.min(200, result.length())));
                } else if (isResultEmpty && district != null && !district.isBlank()) {
                    // Don't save empty district-specific results if we already tried fallback
                    // (fallback would have saved state data if available)
                    logger.debug("Skipping save of empty district-specific result");
                } else {
                    logger.info("Attempting to save performance data to database for state={}, district={}", state, district);
                    dataService.savePerformanceData(result);
                }
            } else {
                logger.debug("Database saving is disabled (useDatabase=false)");
            }
            
            cache.put(cacheKey, result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching performance data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
