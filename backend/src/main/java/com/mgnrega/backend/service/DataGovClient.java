package com.mgnrega.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class DataGovClient {
    private static final Logger logger = LoggerFactory.getLogger(DataGovClient.class);
    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final int maxRetries;

    public DataGovClient(@Value("${datagov.apiKey:}") String apiKey,
                         @Value("${datagov.baseUrl:https://api.data.gov.in/resource}") String baseUrl,
                         @Value("${datagov.maxRetries:3}") int maxRetries) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String fetchResourceJson(String resourceId, Map<String, String> query) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("Missing DATAGOV_API_KEY");
            return "{\"error\":\"Missing DATAGOV_API_KEY environment variable\"}";
        }
        StringBuilder url = new StringBuilder();
        url.append(baseUrl).append("/").append(resourceId)
           .append("?api-key=").append(encode(apiKey))
           .append("&format=json");
        String limit = query.getOrDefault("limit", "100");
        url.append("&limit=").append(encode(limit));
        for (Map.Entry<String, String> e : query.entrySet()) {
            if ("limit".equals(e.getKey())) continue;
            String encodedKey = e.getKey().replace("[", "%5B").replace("]", "%5D");
            url.append("&").append(encodedKey).append("=").append(encode(e.getValue()));
        }
        String finalUrl = url.toString();
        logger.info("DataGov GET: {}", finalUrl.replace(apiKey, "***"));
        URI uri = URI.create(finalUrl);
        
        // Retry logic with exponential backoff
        return webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(2))
                        .filter(throwable -> {
                            logger.warn("Retrying API call: {}", throwable.getMessage());
                            return true;
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            logger.error("Max retries exhausted");
                            return retrySignal.failure();
                        }))
                .onErrorResume(ex -> {
                    logger.error("Error fetching data: {}", ex.getMessage());
                    return Mono.just("{\"error\":\"" + ex.getMessage().replace("\"","'") + "\"}");
                })
                .defaultIfEmpty("{\"error\":\"Empty response from API\"}")
                .block();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
