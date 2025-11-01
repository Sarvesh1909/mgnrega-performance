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
                    
                    // Log first record's keys for debugging field names
                    if (recordsToSave.isEmpty()) {
                        List<String> recordKeys = new ArrayList<>();
                        record.fieldNames().forEachRemaining(recordKeys::add);
                        logger.info("📋 Sample record keys from API: {}", recordKeys);
                        // Log values for debugging
                        for (String key : recordKeys) {
                            JsonNode val = record.get(key);
                            if (val != null && !val.isNull()) {
                                logger.info("  {} = {}", key, val.asText());
                            }
                        }
                    }
                    
                    pr.setFinYear(getStringValue(record, "fin_year"));
                    pr.setMonth(getStringValue(record, "month"));
                    pr.setStateName(getStringValue(record, "state_name"));
                    pr.setDistrictName(getStringValue(record, "district_name"));
                    // Try multiple field name variations for households
                    // API actually uses: "Total_Households_Worked" (from API response)
                    Long households = parseLong(record, "Total_Households_Worked");
                    if (households == null) households = parseLong(record, "No_of_Households_Worked");
                    if (households == null) households = parseLong(record, "households_worked");
                    if (households == null) households = parseLong(record, "Households_Worked");
                    if (households == null) households = parseLong(record, "Number_of_Households_Worked");
                    pr.setHouseholdsWorked(households);
                    
                    // Try multiple field name variations for persondays
                    // API actually uses: "Persondays_of_Central_Liability_so_far" (from API response) - try this FIRST
                    Long persondays = parseLong(record, "Persondays_of_Central_Liability_so_far");
                    if (persondays == null) persondays = parseLong(record, "Total_Persondays_Generated");
                    if (persondays == null) persondays = parseLong(record, "Persondays_Generated");
                    if (persondays == null) persondays = parseLong(record, "persondays_generated");
                    if (persondays == null) persondays = parseLong(record, "Total_Person_Days");
                    if (persondays == null) {
                        // Try calculating from Women_Persondays + SC_persondays + ST_persondays if available
                        Long womenPersondays = parseLong(record, "Women_Persondays");
                        Long scPersondays = parseLong(record, "SC_persondays");
                        Long stPersondays = parseLong(record, "ST_persondays");
                        if (womenPersondays != null || scPersondays != null || stPersondays != null) {
                            long sum = (womenPersondays != null ? womenPersondays : 0) +
                                      (scPersondays != null ? scPersondays : 0) +
                                      (stPersondays != null ? stPersondays : 0);
                            // This is approximate, but better than null
                            if (sum > 0) {
                                persondays = sum;
                                if (recordsToSave.isEmpty()) {
                                    logger.info("  Calculated approximate persondays from components: {}", persondays);
                                }
                            }
                        }
                    }
                    pr.setPersondaysGenerated(persondays);
                    
                    // Try multiple field name variations for women persondays percent
                    // API might have: "Women_Persondays_Percent" or calculate from "Women_Persondays"
                    Double womenPercent = parseDouble(record, "Women_Persondays_Percent");
                    if (womenPercent == null) womenPercent = parseDouble(record, "women_persondays_percent");
                    if (womenPercent == null) womenPercent = parseDouble(record, "percent_of_Women_Persondays");
                    // Don't calculate here yet - wait until after persondays is set
                    pr.setWomenPersondaysPercent(womenPercent);
                    
                    // Try multiple field name variations for ongoing works
                    // API might use various formats, try all possible variations
                    Integer ongoingWorks = parseInt(record, "Number_of_Ongoing_Works");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "No_of_Ongoing_Works");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "Ongoing_Works");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "no_of_ongoing_works");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "OngoingWorks");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "Number_of_works_ongoing");
                    if (ongoingWorks == null) ongoingWorks = parseInt(record, "Works_Ongoing");
                    if (ongoingWorks == null) {
                        // Try to find any field containing "ongoing" or "works"
                        final Integer[] ongoingWorksRef = {null};
                        record.fieldNames().forEachRemaining(fieldName -> {
                            if (fieldName != null && fieldName.toLowerCase().contains("ongoing") && ongoingWorksRef[0] == null) {
                                Integer val = parseInt(record, fieldName);
                                if (val != null) {
                                    ongoingWorksRef[0] = val;
                                    if (recordsToSave.isEmpty()) {
                                        logger.info("  Found ongoing works in field: {} = {}", fieldName, val);
                                    }
                                }
                            }
                        });
                        ongoingWorks = ongoingWorksRef[0];
                    }
                    pr.setNoOfOngoingWorks(ongoingWorks);
                    
                    // Try multiple field name variations for completed works
                    // API might use various formats, try all possible variations
                    Integer completedWorks = parseInt(record, "Number_of_Completed_Works");
                    if (completedWorks == null) completedWorks = parseInt(record, "No_of_Completed_Works");
                    if (completedWorks == null) completedWorks = parseInt(record, "Completed_Works");
                    if (completedWorks == null) completedWorks = parseInt(record, "no_of_completed_works");
                    if (completedWorks == null) completedWorks = parseInt(record, "CompletedWorks");
                    if (completedWorks == null) completedWorks = parseInt(record, "Number_of_works_completed");
                    if (completedWorks == null) completedWorks = parseInt(record, "Works_Completed");
                    if (completedWorks == null) {
                        // Try to find any field containing "completed" or "works"
                        final Integer[] completedWorksRef = {null};
                        record.fieldNames().forEachRemaining(fieldName -> {
                            if (fieldName != null && fieldName.toLowerCase().contains("completed") && completedWorksRef[0] == null) {
                                Integer val = parseInt(record, fieldName);
                                if (val != null) {
                                    completedWorksRef[0] = val;
                                    if (recordsToSave.isEmpty()) {
                                        logger.info("  Found completed works in field: {} = {}", fieldName, val);
                                    }
                                }
                            }
                        });
                        completedWorks = completedWorksRef[0];
                    }
                    pr.setNoOfCompletedWorks(completedWorks);
                    
                    // Try multiple possible field names for average wage rate
                    // Based on logs, API uses "Average_Wage_rate_per_day_per_person"
                    Double avgWage = parseDouble(record, "Average_Wage_rate_per_day_per_person");
                    if (avgWage == null) avgWage = parseDouble(record, "avg_wage_rate");
                    if (avgWage == null) avgWage = parseDouble(record, "average_wage_rate");
                    if (avgWage == null) avgWage = parseDouble(record, "Average_Wage_Rate");
                    pr.setAvgWageRate(avgWage);
                    
                    // Try multiple possible field names for total wages
                    // Based on logs, API uses "Wages" (simple field name) - try this FIRST
                    Double totalWages = parseDouble(record, "Wages");
                    if (totalWages == null) totalWages = parseDouble(record, "Material_and_skilled_Wages");
                    if (totalWages == null) totalWages = parseDouble(record, "total_wages");
                    if (totalWages == null) totalWages = parseDouble(record, "Total_Wages");
                    if (totalWages == null) totalWages = parseDouble(record, "Material and skilled Wages");
                    // The API might return wages in crores - convert if needed (but usually it's already in the right unit)
                    pr.setTotalWages(totalWages);
                    
                    // Calculate women percent from Women_Persondays if we have it but not the percent
                    // Now that persondays is set, we can calculate the percent
                    Long womenPersondays = parseLong(record, "Women_Persondays");
                    if (womenPersondays != null && pr.getWomenPersondaysPercent() == null) {
                        // Get total persondays - use the same variable we just set above
                        Long totalPersondays = persondays; // Use the local variable, not from entity
                        // If still null, try the fields we checked earlier
                        if (totalPersondays == null) {
                            totalPersondays = parseLong(record, "Persondays_of_Central_Liability_so_far");
                        }
                        if (totalPersondays == null) {
                            totalPersondays = parseLong(record, "Total_Persondays_Generated");
                        }
                        if (totalPersondays == null) {
                            totalPersondays = parseLong(record, "Persondays_Generated");
                        }
                        
                        if (totalPersondays != null && totalPersondays > 0) {
                            Double percent = (womenPersondays.doubleValue() / totalPersondays.doubleValue()) * 100.0;
                            pr.setWomenPersondaysPercent(percent);
                            logger.info("  ✅ Calculated Women %: {}% from Women_Persondays={} / Total_Persondays={}", 
                                String.format("%.2f", percent), womenPersondays, totalPersondays);
                        } else {
                            logger.warn("  ⚠️ Cannot calculate Women %: Women_Persondays={}, but Total_Persondays is null. Tried: persondays={}, Persondays_of_Central_Liability_so_far={}", 
                                womenPersondays, persondays, parseLong(record, "Persondays_of_Central_Liability_so_far"));
                        }
                    } else if (womenPersondays == null && pr.getWomenPersondaysPercent() == null) {
                        logger.warn("  ⚠️ Cannot calculate Women %: Women_Persondays is null");
                    }
                    
                    if (recordsToSave.isEmpty()) {
                        logger.info("💰 Parsed data summary:");
                        logger.info("  Households: {}", households);
                        logger.info("  Persondays: {} (from Persondays_of_Central_Liability_so_far={})", 
                            persondays, parseLong(record, "Persondays_of_Central_Liability_so_far"));
                        logger.info("  Women Persondays: {}", parseLong(record, "Women_Persondays"));
                        logger.info("  Women %: {} (calculated: {})", 
                            pr.getWomenPersondaysPercent(), 
                            womenPersondays != null && pr.getPersondaysGenerated() != null && pr.getPersondaysGenerated() > 0 ?
                                String.format("%.2f", (womenPersondays.doubleValue() / pr.getPersondaysGenerated().doubleValue()) * 100.0) : "N/A");
                        logger.info("  Ongoing Works: {}", ongoingWorks);
                        logger.info("  Completed Works: {}", completedWorks);
                        logger.info("  Avg Wage: {}", avgWage);
                        logger.info("  Total Wages: {}", totalWages);
                        
                        // Check for missing fields and log available field names that might match
                        if (ongoingWorks == null || completedWorks == null) {
                            logger.warn("⚠️ Works data missing. Ongoing: {}, Completed: {}", ongoingWorks, completedWorks);
                            List<String> allFields = new ArrayList<>();
                            record.fieldNames().forEachRemaining(allFields::add);
                            // Filter fields that might be related to works
                            List<String> worksFields = new ArrayList<>();
                            for (String f : allFields) {
                                if (f != null && (f.toLowerCase().contains("work") || 
                                    f.toLowerCase().contains("ongoing") || 
                                    f.toLowerCase().contains("completed"))) {
                                    worksFields.add(f);
                                }
                            }
                            if (!worksFields.isEmpty()) {
                                logger.warn("  Available fields that might be works-related: {}", worksFields);
                            }
                        }
                        
                        if (households == null && persondays == null && avgWage == null && totalWages == null 
                            && ongoingWorks == null && completedWorks == null) {
                            logger.warn("⚠️ No data found at all. Checking all available fields...");
                            List<String> allFields = new ArrayList<>();
                            record.fieldNames().forEachRemaining(allFields::add);
                            logger.warn("  All available fields: {}", allFields);
                        }
                    }
                    
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

