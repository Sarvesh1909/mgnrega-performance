package com.mgnrega.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgnrega.backend.entity.PerformanceRecord;
import com.mgnrega.backend.repository.PerformanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PerformanceDataService {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceDataService.class);
    private final PerformanceRecordRepository repository;
    private final ObjectMapper objectMapper;

    public PerformanceDataService(PerformanceRecordRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void savePerformanceData(String jsonResponse) {
        try {
            logger.info("Attempting to save performance data. Response length: {} chars", jsonResponse.length());
            
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Log what keys are available in the root
            List<String> keys = new ArrayList<>();
            root.fieldNames().forEachRemaining(keys::add);
            logger.info("Root JSON keys found: {}", keys);
            
            // Try different possible keys for records
            JsonNode records = root.get("records");
            
            // Check what type records is
            if (records != null && !records.isNull()) {
                logger.info("Records node found. Type: {}, IsArray: {}, IsMissingNode: {}", 
                    records.getNodeType(), records.isArray(), records.isMissingNode());
                
                // If records is not an array, it might be an object or null
                if (!records.isArray()) {
                    if (records.isObject()) {
                        logger.warn("Records is an object, not an array. Keys in records object: {}", records.fieldNames());
                    } else if (records.isTextual()) {
                        logger.warn("Records is text: {}", records.asText());
                    } else {
                        logger.warn("Records node exists but is not an array. Node type: {}", records.getNodeType());
                    }
                }
            } else {
                logger.warn("Records node is null or missing");
                records = root.path("records"); // Use path to get MissingNode
            }
            
            // If records is missing or empty, try "data" 
            if (records == null || records.isNull() || records.isMissingNode() || !records.isArray() || records.isEmpty()) {
                logger.debug("Records array not found or empty, trying alternative keys...");
                JsonNode data = root.get("data");
                if (data != null && data.isArray() && !data.isEmpty()) {
                    records = data;
                    logger.info("Found data in 'data' array instead");
                }
            }
            
            // Final check if we found a valid array
            // Note: Empty array (size 0) is valid - it means API returned no matching records
            if (records == null || records.isNull() || records.isMissingNode() || !records.isArray()) {
                logger.error("No valid records array found in response. Available keys: {}", keys);
                logger.error("Records node status - IsNull: {}, IsMissing: {}, IsArray: {}, Size: {}", 
                    records == null || records.isNull(), 
                    records != null && records.isMissingNode(),
                    records != null && records.isArray(),
                    records != null && records.isArray() ? records.size() : 0);
                
                // Check the actual records node more carefully
                JsonNode recordsNode = root.get("records");
                if (recordsNode != null) {
                    logger.error("Records node details: nodeType={}, isArray={}, isNull={}, isMissing={}, isEmpty={}", 
                        recordsNode.getNodeType(), recordsNode.isArray(), recordsNode.isNull(), 
                        recordsNode.isMissingNode(), recordsNode.isEmpty());
                    
                    // Try to see what records actually contains
                    if (recordsNode.isTextual()) {
                        logger.error("Records is text: {}", recordsNode.asText());
                    } else if (recordsNode.isObject()) {
                        List<String> recordKeys = new ArrayList<>();
                        recordsNode.fieldNames().forEachRemaining(recordKeys::add);
                        logger.error("Records is an object with keys: {}", recordKeys);
                    } else if (recordsNode.isArray()) {
                        logger.error("Records IS an array! Size: {}", recordsNode.size());
                    }
                }
                
                // Log more of the response to see if records appears later
                try {
                    // Check raw response for "records" keyword to see context
                    int recordsIndex = jsonResponse.indexOf("\"records\"");
                    if (recordsIndex > 0) {
                        int start = Math.max(0, recordsIndex - 100);
                        int end = Math.min(jsonResponse.length(), recordsIndex + 500);
                        logger.error("Context around 'records' in raw JSON (chars {} to {}): {}", 
                            start, end, jsonResponse.substring(start, end));
                    }
                    
                    // Also check end of response where data might be
                    if (jsonResponse.length() > 3000) {
                        logger.error("End of JSON response (last 1500 chars): {}", 
                            jsonResponse.substring(jsonResponse.length() - 1500));
                    }
                } catch (Exception e) {
                    logger.error("Could not inspect JSON structure: {}", e.getMessage());
                }
                return;
            }

            logger.info("Found {} records in API response", records.size());
            
            // If records array is empty, log and return (no data to save)
            if (records.isEmpty()) {
                logger.warn("Records array is empty - no data to save. This means the API returned 0 matching records.");
                return;
            }
            
            List<PerformanceRecord> recordsToSave = new ArrayList<>();
            
            for (JsonNode record : records) {
                try {
                    PerformanceRecord pr = new PerformanceRecord();
                    
                    pr.setFinYear(getStringValue(record, "fin_year"));
                    pr.setMonth(getStringValue(record, "month"));
                    pr.setStateName(getStringValue(record, "state_name"));
                    pr.setDistrictName(getStringValue(record, "district_name"));
                    pr.setHouseholdsWorked(parseLong(record, "households_worked"));
                    pr.setPersondaysGenerated(parseLong(record, "persondays_generated"));
                    pr.setWomenPersondaysPercent(parseDouble(record, "women_persondays_percent"));
                    pr.setNoOfOngoingWorks(parseInt(record, "no_of_ongoing_works"));
                    pr.setNoOfCompletedWorks(parseInt(record, "no_of_completed_works"));
                    pr.setAvgWageRate(parseDouble(record, "avg_wage_rate"));
                    pr.setTotalWages(parseDouble(record, "total_wages"));
                    
                    // Only add if we have at least state and district
                    if (pr.getStateName() != null && pr.getDistrictName() != null) {
                        recordsToSave.add(pr);
                        logger.debug("Parsed record: {} - {} ({}/{})", 
                            pr.getDistrictName(), pr.getStateName(), pr.getMonth(), pr.getFinYear());
                    } else {
                        logger.warn("Skipping record with missing state or district: state={}, district={}", 
                            pr.getStateName(), pr.getDistrictName());
                    }
                } catch (Exception e) {
                    logger.error("Error parsing individual record: {}", e.getMessage(), e);
                }
            }
            
            if (recordsToSave.isEmpty()) {
                logger.warn("No valid records to save after parsing");
                return;
            }
            
            repository.saveAll(recordsToSave);
            logger.info("✅ Successfully saved {} performance records to database", recordsToSave.size());
        } catch (Exception e) {
            logger.error("❌ Error saving performance data: {}", e.getMessage(), e);
            logger.error("Response that failed: {}", jsonResponse.substring(0, Math.min(1000, jsonResponse.length())));
        }
    }

    public List<PerformanceRecord> getFromDatabase(String stateName, String districtName, int limit) {
        List<PerformanceRecord> records = repository.findRecentByDistrict(stateName, districtName);
        return records.size() > limit ? records.subList(0, limit) : records;
    }

    public List<PerformanceRecord> getStateData(String stateName, int limit) {
        List<PerformanceRecord> records = repository.findRecentByState(stateName);
        return records.size() > limit ? records.subList(0, limit) : records;
    }

    private String getStringValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }

    private Long parseLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            String str = value.asText().replace(",", "").trim();
            return str.isEmpty() ? null : Long.parseLong(str);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            String str = value.asText().replace(",", "").trim();
            return str.isEmpty() ? null : Integer.parseInt(str);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            String str = value.asText().replace(",", "").trim();
            return str.isEmpty() ? null : Double.parseDouble(str);
        } catch (Exception e) {
            return null;
        }
    }
}

