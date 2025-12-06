package com.intellidocAI.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidocAI.backend.config.KafkaTopicConfig;
import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.repository.DocumentationRepository;
import com.intellidocAI.backend.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RepositoryProcessingWorker {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryProcessingWorker.class);
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024; // 100KB limit per file

    @Autowired private GitService gitService;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private DocumentationRepository documentationRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;

    // 2. INJECT WEBSOCKET TEMPLATE
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final WebClient webClient;
    // Used to parse the JSON response from the Python Architect Agent
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RepositoryProcessingWorker(WebClient.Builder webClientBuilder, @Value("${ai.service.url:http://localhost:8000}") String aiUrl) {
        this.webClient = webClientBuilder
                .baseUrl(aiUrl)
                // Increase buffer to 10MB to allow sending/receiving large file trees
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @KafkaListener(topics = KafkaTopicConfig.REPO_PROCESSING_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void processRepository(RepositoryProcessingRequest request) {
        Repository repository = repositoryRepository.findById(request.getRepositoryId()).orElse(null);
        if (repository == null) return;

        try {
            if (request.getActionType() == RepositoryProcessingRequest.ActionType.ANALYZE_CODE) {
                performCodeAnalysis(repository, request);
            } else if (request.getActionType() == RepositoryProcessingRequest.ActionType.GENERATE_README) {
                performReadmeGeneration(repository);
            }
        } catch (Exception e) {
            logger.error("CRITICAL FAILURE for {}", repository.getName(), e);
            repository.setStatus("FAILED");
            repositoryRepository.save(repository);
            // Optional: Broadcast FAILURE status too
            messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(), Map.of("status", "FAILED"));
        }
    }

    // ============================================================================================
    // 🧠 ACTION 1: AGENTIC CODE ANALYSIS (The Architect Workflow)
    // ============================================================================================
    private void performCodeAnalysis(Repository repository, RepositoryProcessingRequest request) {
        repository.setStatus("ANALYZING_CODE");
        repositoryRepository.save(repository);
        messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(), Map.of("status", "ANALYZING_CODE"));

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("intellidoc_" + request.getRepositoryId());
            gitService.cloneRepository(request.getRepoUrl(), tempDir.toAbsolutePath().toString());

            String projectContext = generateProjectContext(tempDir);
            repository.setProjectStructure(projectContext);

            // --- 1. ARCHITECT SELECTION (CACHED) ---
            List<String> selectedFiles;
            String architectCacheKey = "architect:" + toSha256(projectContext); // Hash the tree structure

            // Check Cache first
            String cachedSelection = redisTemplate.opsForValue().get(architectCacheKey);

            if (cachedSelection != null) {
                logger.info("♻️ Architect Cache Hit! Skipping AI consultation.");
                try {
                    selectedFiles = objectMapper.readValue(cachedSelection, new TypeReference<List<String>>(){});
                } catch (Exception e) {
                    logger.warn("Failed to parse cached architect response, re-fetching.");
                    selectedFiles = callAiFileSelector(projectContext).block();
                }
            } else {
                logger.info("🤖 Consulting AI Architect (Fresh Analysis)...");
                selectedFiles = callAiFileSelector(projectContext).block();

                // Cache the result for 7 days
                if (selectedFiles != null && !selectedFiles.isEmpty()) {
                    try {
                        String json = objectMapper.writeValueAsString(selectedFiles);
                        redisTemplate.opsForValue().set(architectCacheKey, json, Duration.ofDays(7));
                    } catch (JsonProcessingException e) {
                        logger.warn("Failed to cache architect response");
                    }
                }
            }

            // --- 2. Map & Filter ---
            List<Path> filesToProcess = new ArrayList<>();
            Path finalTempDir = tempDir;

            if (selectedFiles == null || selectedFiles.isEmpty()) {
                logger.warn("Architect Agent returned no files. Falling back to Heuristic.");
                filesToProcess = fallbackSelection(tempDir);
            } else {
                try (Stream<Path> paths = Files.walk(tempDir)) {
                    List<String> finalSelectedFiles = selectedFiles;
                    filesToProcess = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                String relPath = finalTempDir.relativize(path).toString().replace("\\", "/");
                                return finalSelectedFiles.stream().anyMatch(f -> relPath.endsWith(f));
                            })
                            .limit(20)
                            .collect(Collectors.toList());
                }
            }

            if (filesToProcess.isEmpty()) {
                filesToProcess = fallbackSelection(tempDir);
            }

            logger.info("Processing {} files...", filesToProcess.size());

            // --- 3. Process Files (Smart Throttling) ---
            Flux.fromIterable(filesToProcess)
                    // REMOVED GLOBAL DELAY here to make Cache Hits fast!
                    .flatMap(filePath -> {
                        String relativePath = finalTempDir.relativize(filePath).toString();
                        String fileContent = readString(filePath);
                        if (fileContent == null || fileContent.isBlank()) return Mono.empty();

                        String cacheKey = "doc:" + toSha256(fileContent);

                        // Check Redis Cache
                        String cachedDoc = redisTemplate.opsForValue().get(cacheKey);
                        if (cachedDoc != null) {
                            logger.info("♻️ Cache Hit for: {}", relativePath); // Explicit Log
                            saveDocumentation(repository.getId(), relativePath, cachedDoc);
                            return Mono.empty();
                        }

                        logger.info("🤖 AI Generating for: {}", relativePath); // Explicit Log
                        String prompt = createPrompt(fileContent, projectContext, relativePath);

                        return callPythonService(prompt)
                                // Add Delay ONLY for AI calls (Smart Throttling)
                                .delaySubscription(Duration.ofMillis(1200))
                                .doOnNext(docContent -> {
                                    if (docContent.startsWith("Error:")) {
                                        logger.warn("⚠️ AI Failed for file: {} (Skipping save)", relativePath);
                                    } else {
                                        saveDocumentation(repository.getId(), relativePath, docContent);
                                        redisTemplate.opsForValue().set(cacheKey, docContent, Duration.ofDays(7));
                                    }
                                });
                    }, 2)
                    .blockLast();

            repository.setStatus("ANALYSIS_COMPLETED");
            repository.setLastAnalyzedAt(LocalDateTime.now());
            repositoryRepository.save(repository);

            // 3. 🔥 BROADCAST COMPLETION EVENT (ANALYSIS)
            messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(), Map.of("status", "ANALYSIS_COMPLETED"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    // ============================================================================================
    // 🔌 API CLIENTS
    // ============================================================================================

    // 1. CALL ARCHITECT (Select Files)
    private Mono<List<String>> callAiFileSelector(String fileTree) {
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
                .timeout(Duration.ofSeconds(45)) // Architect needs time to think
                .onErrorResume(e -> {
                    logger.error("Architect Agent Error: {}", e.getMessage());
                    return Mono.just(new ArrayList<>());
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)));
    }

    // 2. CALL WRITER (Generate Docs)
    private Mono<String> callPythonService(String prompt) {
        return webClient.post()
                .uri("/generate-docs")
                .bodyValue(Map.of("prompt", prompt))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.getOrDefault("documentation", "Error: AI response missing content."))
                .timeout(Duration.ofSeconds(90)) // 🔥 INCREASED TIMEOUT to 90s for large files
                .onErrorResume(e -> {
                    logger.warn("AI Service Error: {}", e.getMessage());
                    return Mono.just("Error: AI Service Timeout or Failure. " + e.getMessage());
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))); // Increased backoff delay
    }

    // ============================================================================================
    // 🛠️ HELPERS & FALLBACKS
    // ============================================================================================

    // The "Safety Net": If AI Architect fails, use our old smart scoring logic
    private List<Path> fallbackSelection(Path tempDir) throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isValidCodeFile)
                    .sorted(Comparator.comparingInt(this::calculateFileScore).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
        }
    }

    private String generateProjectContext(Path rootDir) {
        StringBuilder sb = new StringBuilder("Project Structure:\n");
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isNotJunk) // Basic pre-filter to keep tree clean
                    .limit(300) // Don't send massive trees
                    .forEach(path -> sb.append(rootDir.relativize(path).toString().replace("\\", "/")).append("\n"));
        } catch (IOException e) { return ""; }
        return sb.toString();
    }

    // Basic filter for Tree Generation (Don't confuse Architect with node_modules)
    private boolean isNotJunk(Path path) {
        String abs = path.toString();
        if (abs.contains(".git") || abs.contains("node_modules") || abs.contains("target") || abs.contains("__pycache__")) return false;
        String name = path.getFileName().toString().toLowerCase();
        return !name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".class") && !name.endsWith(".jar");
    }

    // Strict filter for Fallback Logic
    private boolean isValidCodeFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        String absolutePath = path.toString();

        // 1. 🛑 HARD BLOCKLIST (Directories)
        if (absolutePath.contains(File.separator + ".git") ||
                absolutePath.contains(File.separator + "node_modules") ||
                absolutePath.contains(File.separator + "target") ||
                absolutePath.contains(File.separator + "build") ||
                absolutePath.contains(File.separator + "dist") ||
                absolutePath.contains(File.separator + "bin") ||
                absolutePath.contains(File.separator + "obj") ||
                absolutePath.contains(File.separator + "__pycache__") ||
                absolutePath.contains(File.separator + ".idea") ||
                absolutePath.contains(File.separator + ".vscode")) {
            return false;
        }

        // 2. 🛑 HARD BLOCKLIST (Extensions) - Added .sh, Dockerfile, etc.
        List<String> blockedExts = Arrays.asList(
                // Scripts & Configs (Noise)
                ".sh", ".bat", ".cmd", ".ps1", "dockerfile", "makefile", "jenkinsfile",
                ".yml", ".yaml", ".xml", ".json", ".conf", ".properties", ".ini", ".env",
                ".txt", ".md", ".csv", ".sql", ".svg", ".css", ".html",
                // IDE & Binaries
                ".iml", ".class", ".jar", ".war", ".exe", ".dll", ".so",
                ".suo", ".sln", ".user", ".lock", ".log"
        );

        if (blockedExts.stream().anyMatch(filename::endsWith)) return false;

        // 3. ⚖️ SIZE CHECK
        try {
            if (Files.size(path) > MAX_FILE_SIZE_BYTES) return false;
        } catch (IOException e) { return false; }

        return !isBinaryFile(path);
    }

    private int calculateFileScore(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.matches("^(app|main|server|index|application)\\.(java|py|js|ts)$")) return 100;
        return 10;
    }

    // --- Standard Methods (Read/Write/Readme) ---
    private void performReadmeGeneration(Repository repository) {
        repository.setStatus("GENERATING_README");
        repositoryRepository.save(repository);
        try {
            String projectStructure = repository.getProjectStructure();
            List<Documentation> existingDocs = documentationRepository.findByRepositoryId(repository.getId());

            StringBuilder summariesContext = new StringBuilder();
            for (Documentation doc : existingDocs) {
                if(doc.getFilePath().contains("README_GENERATED")) continue;
                String purpose = extractPurpose(doc.getContent());
                summariesContext.append("- **").append(doc.getFilePath()).append("**: ").append(purpose).append("\n");
            }
            if (summariesContext.length() == 0) summariesContext.append("No files were successfully analyzed.");

            // 🔥 UPGRADED HIGH-LEVEL PROMPT 🔥
            String prompt = String.format("""
                You are a **Chief Technology Officer (CTO)** writing documentation.
                Generate a **World-Class `README.md`** that makes this project look production-ready.

                **1. PROJECT CONTEXT:**
                ```
                %s
                ```
                
                **2. CODE INTELLIGENCE (What the code actually does):**
                %s
                
                **3. REQUIRED SECTIONS (Use proper Markdown # Headers):**
                * **Project Title** (and a punchy tagline).
                * **Badges:** Add 3-5 Shield.io badges for the tech stack (e.g., Java, Python, React).
                * **Executive Summary:** A professional summary of the problem and solution.
                * **Key Features:** Technical highlights inferred from the code.
                * **Tech Stack:** A clean table or list.
                * **Getting Started:** Infer installation steps (e.g., `npm install`, `pip install`, `mvn clean install`) based on file types.
                * **Architecture:** Briefly explain the folder structure/modules.
                
                **OUTPUT RULES:**
                * Use emojis sparingly but effectively (e.g., 🚀 🛠️).
                * Keep it concise, technical, and professional.
                * **Output ONLY the raw Markdown.**
                """, projectStructure, summariesContext.toString());

            String readmeContent = callPythonService(prompt).block();
            if (readmeContent != null && !readmeContent.startsWith("Error:")) {
                saveDocumentation(repository.getId(), "README_GENERATED.md", readmeContent);
                repository.setStatus("COMPLETED");

                // 4. 🔥 BROADCAST COMPLETION EVENT (README)
                messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(), Map.of("status", "COMPLETED"));

            } else { repository.setStatus("FAILED"); }
            repositoryRepository.save(repository);
        } catch (Exception e) {
            repository.setStatus("FAILED");
            repositoryRepository.save(repository);
        }
    }

    private String extractPurpose(String fullDoc) {
        if (fullDoc == null) return "";
        int start = fullDoc.indexOf("Purpose");
        if (start == -1) return "Contains code logic.";
        int end = fullDoc.indexOf("###", start + 5);
        if (end == -1) end = Math.min(fullDoc.length(), start + 200);
        return fullDoc.substring(start, end).replace("### 🎯 Purpose", "").trim();
    }

    private String createPrompt(String fileContent, String projectContext, String fileName) {
        return String.format("""
            You are a senior software architect. Document: **%s**.
            CONTEXT: %s
            CONTENT: %s
            OUTPUT: Markdown. 1. Purpose 2. Components 3. Dependencies.
            """, fileName, projectContext, fileContent);
    }

    private void saveDocumentation(String repositoryId, String filePath, String content) {
        Documentation newDoc = new Documentation();
        newDoc.setRepositoryId(repositoryId);
        newDoc.setFilePath(filePath);
        newDoc.setContent(content);
        documentationRepository.save(newDoc);
        logger.info("Saved docs for: {}", filePath);
    }

    private boolean isBinaryFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[512];
            int bytesRead = in.read(buffer);
            for (int i = 0; i < bytesRead; i++) if (buffer[i] == 0) return true;
            return false;
        } catch (IOException e) { return true; }
    }

    private String readString(Path path) {
        try { return Files.readString(path, StandardCharsets.UTF_8); }
        catch (IOException e) { return null; }
    }

    private String toSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) { return String.valueOf(text.hashCode()); }
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir != null) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) { logger.warn("Cleanup failed", e); }
        }
    }
}