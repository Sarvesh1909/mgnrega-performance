package com.mgnrega.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);
    
    // Allow max 10 requests per minute to data.gov.in API
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long TIME_WINDOW_MS = 60_000; // 1 minute
    
    private final Map<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();
    
    private static class RequestWindow {
        private int count;
        private long windowStart;
        
        RequestWindow() {
            this.count = 1;
            this.windowStart = Instant.now().toEpochMilli();
        }
    }
    
    public synchronized boolean allowRequest(String key) {
        long now = Instant.now().toEpochMilli();
        RequestWindow window = requestWindows.get(key);
        
        if (window == null) {
            requestWindows.put(key, new RequestWindow());
            return true;
        }
        
        // Reset window if expired
        if (now - window.windowStart > TIME_WINDOW_MS) {
            window.count = 1;
            window.windowStart = now;
            return true;
        }
        
        // Check if limit exceeded
        if (window.count >= MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Rate limit exceeded for key: {}. Count: {}", key, window.count);
            return false;
        }
        
        window.count++;
        return true;
    }
    
    public void reset(String key) {
        requestWindows.remove(key);
    }
    
    // Cleanup old windows periodically
    public void cleanup() {
        long now = Instant.now().toEpochMilli();
        requestWindows.entrySet().removeIf(entry -> 
            now - entry.getValue().windowStart > TIME_WINDOW_MS);
    }
}

