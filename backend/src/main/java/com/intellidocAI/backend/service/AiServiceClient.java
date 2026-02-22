package com.intellidocAI.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating API calls to the Python AI microservice.
 */
@Service
public class AiServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(AiServiceClient.class);

    private final WebClient webClient;

    @Autowired
    public AiServiceClient(WebClient.Builder webClientBuilder,
            @Value("${ai.service.url:http://localhost:8000}") String aiUrl) {
        this.webClient = webClientBuilder
                .baseUrl(aiUrl)
                // Increase buffer to 10MB to allow sending/receiving large file trees
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * CALL ARCHITECT (Select Files) - Analyzes the project structure and selects
     * the most relevant files.
     */
    @SuppressWarnings("unchecked")
    public Mono<List<String>> callAiFileSelector(String fileTree) {
        return webClient.post()
                .uri("/select-files")
                .bodyValue(Map.of("file_structure", fileTree))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    try {
                        Object filesObj = response.get("selected_files");
                        if (filesObj instanceof List) {
                            return (List<String>) filesObj;
                        }
                        return new ArrayList<String>();
                    } catch (Exception e) {
                        logger.error("Failed to parse AI selection", e);
                        return new ArrayList<String>();
                    }
                })
                .timeout(Duration.ofSeconds(75))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    logger.error("Architect Agent Error: {}", e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * CALL BATCH WRITER (Generate Docs for multiple files in one API call)
     */
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, String>>> callBatchPythonService(
            List<Map<String, String>> files, String projectContext) {
        return webClient.post()
                .uri("/generate-docs-batch")
                .bodyValue(Map.of(
                        "files", files,
                        "project_context", projectContext))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object resultsObj = response.get("results");
                    if (resultsObj instanceof List) {
                        return (List<Map<String, String>>) resultsObj;
                    }
                    return new ArrayList<Map<String, String>>();
                })
                .timeout(Duration.ofSeconds(300))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3)))
                .onErrorResume(e -> {
                    logger.warn("Batch AI Service Error: {}", e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * CALL SINGLE WRITER (Used for single file doc or README generation)
     */
    public Mono<String> callPythonService(String prompt) {
        return webClient.post()
                .uri("/generate-docs")
                .bodyValue(Map.of("prompt", prompt))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.getOrDefault("documentation", "Error: AI response missing content."))
                .timeout(Duration.ofSeconds(300))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3)))
                .onErrorResume(e -> {
                    logger.warn("AI Service Error: {}", e.getMessage());
                    return Mono.just("Error: AI Service Timeout or Failure. " + e.getMessage());
                });
    }
}
