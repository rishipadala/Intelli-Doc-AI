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

    @Autowired
    private GitService gitService;
    @Autowired
    private RepositoryRepository repositoryRepository;
    @Autowired
    private DocumentationRepository documentationRepository;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 2. INJECT WEBSOCKET TEMPLATE
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final WebClient webClient;
    // Used to parse the JSON response from the Python Architect Agent
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RepositoryProcessingWorker(WebClient.Builder webClientBuilder,
            @Value("${ai.service.url:http://localhost:8000}") String aiUrl) {
        this.webClient = webClientBuilder
                .baseUrl(aiUrl)
                // Increase buffer to 10MB to allow sending/receiving large file trees
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @KafkaListener(topics = KafkaTopicConfig.REPO_PROCESSING_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void processRepository(RepositoryProcessingRequest request) {
        Repository repository = repositoryRepository.findById(request.getRepositoryId()).orElse(null);
        if (repository == null)
            return;

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
                logger.info("Architect Cache Hit! Skipping AI consultation.");
                try {
                    selectedFiles = objectMapper.readValue(cachedSelection, new TypeReference<List<String>>() {
                    });
                } catch (Exception e) {
                    logger.warn("Failed to parse cached architect response, re-fetching.");
                    selectedFiles = callAiFileSelector(projectContext).block();
                }
            } else {
                logger.info("Consulting AI Architect (Fresh Analysis)...");
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
                        if (fileContent == null || fileContent.isBlank())
                            return Mono.empty();

                        String cacheKey = "doc:" + toSha256(fileContent);

                        // Check Redis Cache
                        String cachedDoc = redisTemplate.opsForValue().get(cacheKey);
                        if (cachedDoc != null) {
                            logger.info("Cache Hit for: {}", relativePath); // Explicit Log
                            saveDocumentation(repository.getId(), relativePath, cachedDoc);
                            return Mono.empty();
                        }

                        logger.info("AI Generating for: {}", relativePath); // Explicit Log
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
            messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(),
                    Map.of("status", "ANALYSIS_COMPLETED"));

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
                .timeout(Duration.ofSeconds(75)) // Architect needs time to think (longer prompt)
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
                .timeout(Duration.ofSeconds(120)) // Increased timeout for richer structured output
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
                    .filter(this::isNotJunk)
                    // 🔥 NEW: Sort vital config files to the TOP
                    .sorted((p1, p2) -> {
                        String f1 = p1.getFileName().toString().toLowerCase();
                        String f2 = p2.getFileName().toString().toLowerCase();
                        if (isVitalConfig(f1))
                            return -1; // f1 comes first
                        if (isVitalConfig(f2))
                            return 1; // f2 comes first
                        return p1.compareTo(p2); // Default alphabetical
                    })
                    .limit(300)
                    .forEach(path -> sb.append(rootDir.relativize(path).toString().replace("\\", "/")).append("\n"));
        } catch (IOException e) {
            return "";
        }
        return sb.toString();
    }

    // Helper for the sorting logic
    private boolean isVitalConfig(String filename) {
        return filename.equals("pom.xml") ||
                filename.equals("build.gradle") ||
                filename.equals("dockerfile") ||
                filename.equals("requirements.txt") ||
                filename.equals("package.json");
    }

    // Basic filter for Tree Generation (Don't confuse Architect with node_modules)
    private boolean isNotJunk(Path path) {
        String abs = path.toString();
        if (abs.contains(".git") || abs.contains("node_modules") || abs.contains("target")
                || abs.contains("__pycache__"))
            return false;
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
                ".suo", ".sln", ".user", ".lock", ".log");

        if (blockedExts.stream().anyMatch(filename::endsWith))
            return false;

        // 3. ⚖️ SIZE CHECK
        try {
            if (Files.size(path) > MAX_FILE_SIZE_BYTES)
                return false;
        } catch (IOException e) {
            return false;
        }

        return !isBinaryFile(path);
    }

    private int calculateFileScore(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.matches("^(app|main|server|index|application)\\.(java|py|js|ts)$"))
            return 100;
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
                if (doc.getFilePath().contains("README_GENERATED"))
                    continue;
                String purpose = extractPurpose(doc.getContent());
                summariesContext.append("- **").append(doc.getFilePath()).append("**: ").append(purpose).append("\n");
            }
            if (summariesContext.length() == 0)
                summariesContext.append("No files were successfully analyzed.");

            // HIGH-LEVEL PROMPT
            String prompt = String.format(
                    """
                            You are an **Elite Senior Technical Writer** and **Open Source Documentation Architect**.
                            Your task is to transform the following code analysis into a **stunning, professional, production-grade `README.md`** that makes developers excited to explore and contribute to this project.

                            ---

                            ### 1. INPUT DATA
                            **Project Structure:**
                            %s

                            **Code Intelligence (AI-Analyzed File Summaries):**
                            %s

                            ---

                            ### 2. DEEP ANALYSIS INSTRUCTIONS (Internal — Do NOT output this)
                            * **Detect Stack:** Analyze file extensions, directory structure, and config files to identify ALL technologies:
                                * `pom.xml` / `build.gradle` → Java/Spring Boot
                                * `requirements.txt` / `pyproject.toml` → Python
                                * `package.json` → Node.js/React/Vue/Angular
                                * `Dockerfile` / `docker-compose.yml` → Docker
                                * `application.properties/.yml` → Spring Boot config
                                * `.env` files → Environment configuration present
                            * **Infer Build & Run Commands:**
                                * Java/Maven: `./mvnw clean install` + `mvn spring-boot:run`
                                * Java/Gradle: `./gradlew build` + `./gradlew bootRun`
                                * Python/pip: `pip install -r requirements.txt` + `python app.py` or `uvicorn app:app`
                                * Node.js: `npm install` + `npm run dev` or `npm start`
                            * **Infer Architecture Pattern:** Monolith, Microservices, Modular Monolith, Event-Driven, Layered?
                            * **Detect Infrastructure:** Kafka, Redis, MongoDB, PostgreSQL, RabbitMQ, Docker, etc.
                            * **Identify API Style:** REST, GraphQL, gRPC, WebSocket?
                            * **Extract Environment Variables:** From `.env`, `application.properties`, or `docker-compose.yml` references.

                            ### 3. OUTPUT STRUCTURE (Follow this EXACTLY)

                            **HEADER BLOCK**
                            * **Title**: Use a creative H1 like: `# 🚀 ProjectName — One-Line Tagline`
                            * **Badges Row**: Generate 4-6 `shields.io` badges using `style=for-the-badge`.
                                * **FORMAT**: `![Label](https://img.shields.io/badge/Label-Value-color?style=for-the-badge&logo=logoname)`
                                * Include: Primary Language, Framework(s), Database, License
                                * Use accurate logo names from shields.io (e.g., `logo=spring`, `logo=react`, `logo=python`, `logo=docker`)
                                * Use brand-appropriate colors (e.g., Spring=6DB33F, React=61DAFB, Python=3776AB, Java=ED8B00, Docker=2496ED)
                                * ❌ DO NOT use HTML `<img>` tags. Use ONLY Markdown image syntax.
                            * **Elevator Pitch**: 2-3 sentences explaining what this project does, who it's for, and why it matters.

                            **TABLE OF CONTENTS**
                            * Use emoji-prefixed links: `- [📍 Overview](#-overview)` etc.
                            * Include all major sections.

                            **## 📍 Overview**
                            * Explain the project's purpose in depth (3-5 sentences).
                            * Describe the high-level architecture (e.g., "Built on a microservices architecture with Spring Boot handling API orchestration, Kafka for async messaging, and a Python AI service for document generation.").
                            * Mention the problem it solves and the target audience.

                            **## 🏗️ Architecture**
                            * If the project has multiple services/modules, include an ASCII or text-based architecture diagram:
                            ```
                            ┌─────────────┐    ┌──────────┐    ┌────────────┐
                            │   Frontend  │───▶│  Backend │───▶│ AI Service │
                            │  (React)    │    │ (Spring) │    │ (FastAPI)  │
                            └─────────────┘    └──────────┘    └────────────┘
                            ```
                            * Explain the data flow between components.

                            **## 👾 Tech Stack**
                            * Use a **Markdown table** with columns: Category | Technology | Purpose
                            * Categories: Core, Database, Infrastructure, DevTools

                            **## ✨ Key Features**
                            * Extract 4-6 compelling features from the code intelligence.
                            * Use format: `- **🔥 Feature Name** — Brief description of what it does and why it matters.`
                            * Focus on unique/impressive capabilities, not generic ones.

                            **## 🚀 Getting Started**

                            * **Prerequisites**: List with version numbers (e.g., "Java 17+", "Node.js 18+", "Docker")
                            * **Installation**:
                                1. Clone: `git clone <repo-url>`
                                2. Navigate: `cd project-name`
                                3. Install dependencies (use detected commands)
                                4. Configure environment (mention `.env` or config files if detected)
                            * **Running the Application**: Provide exact run commands for each service/component.
                            * **Verify It Works**: Suggest a quick smoke test (e.g., `curl http://localhost:8080/api/health`).

                            **## 🔗 API Endpoints** (ONLY if REST controllers/routes are detected)
                            * Use a table: Method | Endpoint | Description
                            * Extract from controller/route file summaries. Include Auth endpoints, CRUD endpoints, etc.
                            * If no API is detected, SKIP this section entirely.

                            **## 📂 Project Structure**
                            * Show the top-level directory tree in a code block using box-drawing characters:
                            ```
                            ├── src/main/java/      # Backend source code
                            ├── frontend/           # React frontend
                            ├── ai-service/         # Python AI microservice
                            └── docker-compose.yml  # Container orchestration
                            ```
                            * Explain what each top-level folder contains.

                            **## ⚙️ Environment Variables** (ONLY if .env or config files detected)
                            * Table format: Variable | Description | Default
                            * Extract from `.env`, `application.properties`, or `docker-compose.yml` references.
                            * If no env vars detected, SKIP this section.

                            **## 🤝 Contributing**
                            * Brief contributing guidelines:
                                1. Fork the repository
                                2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
                                3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
                                4. Push to the branch (`git push origin feature/AmazingFeature`)
                                5. Open a Pull Request

                            **## 📄 License**
                            * If LICENSE file detected, mention it. Otherwise use: "Distributed under the MIT License."

                            ---

                            ### 4. QUALITY RULES
                            * **Tone**: Professional yet approachable. Write as if onboarding a new team member.
                            * **Depth over breadth**: Better to explain 5 features well than list 20 superficially.
                            * **Code blocks**: Use fenced code blocks (```) for ALL commands, paths, and file names.
                            * **Accuracy**: Only mention technologies you can confirm from the file structure and summaries.
                            * **Badge rule**: ONLY use Markdown `![text](url)` syntax. NEVER use HTML `<img>` tags.
                            * **CRITICAL**: Do NOT wrap the entire output in a markdown code block. Return raw markdown text ONLY.
                            * **Output**: RETURN ONLY THE RAW MARKDOWN CONTENT. NO preamble, NO explanations, NO "Here is your README".
                            """,
                    projectStructure, summariesContext.toString());

            String readmeContent = callPythonService(prompt).block();
            if (readmeContent != null && !readmeContent.startsWith("Error:")) {
                saveDocumentation(repository.getId(), "README_GENERATED.md", readmeContent);
                repository.setStatus("COMPLETED");

                // 4. 🔥 BROADCAST COMPLETION EVENT (README)
                messagingTemplate.convertAndSend("/topic/repo/" + repository.getId(), Map.of("status", "COMPLETED"));

            } else {
                repository.setStatus("FAILED");
            }
            repositoryRepository.save(repository);
        } catch (Exception e) {
            repository.setStatus("FAILED");
            repositoryRepository.save(repository);
        }
    }

    private String extractPurpose(String fullDoc) {
        if (fullDoc == null)
            return "";
        int start = fullDoc.indexOf("Purpose");
        if (start == -1)
            return "Contains code logic.";
        int end = fullDoc.indexOf("###", start + 5);
        if (end == -1)
            end = Math.min(fullDoc.length(), start + 200);
        return fullDoc.substring(start, end).replace("### 🎯 Purpose", "").trim();
    }

    private String createPrompt(String fileContent, String projectContext, String fileName) {
        return String.format(
                """
                        You are a **Senior Staff Engineer** writing internal technical documentation for your team.
                        Analyze the following source file with the depth and precision of a thorough code review.

                        ---

                        **FILE:** `%s`

                        **PROJECT CONTEXT (File Tree):**
                        ```
                        %s
                        ```

                        **SOURCE CODE:**
                        ```
                        %s
                        ```

                        ---

                        ### DOCUMENTATION REQUIREMENTS

                        Produce a comprehensive Markdown document with the following sections:

                        **### 🎯 Purpose**
                        * What does this file/class/module do? (2-3 sentences)
                        * Where does it fit in the overall architecture? (e.g., "This is the service layer that handles business logic for...")
                        * What problem does it solve?

                        **### 🏗️ Architecture & Design**
                        * What design patterns are used? (e.g., Repository Pattern, Strategy, Observer, Builder, Factory)
                        * How does this component interact with other parts of the system?
                        * What is the class hierarchy or module structure?
                        * Mention any important interfaces implemented or abstract classes extended.

                        **### 📦 Key Components**
                        * Use a **Markdown table** with columns: Component | Type | Description
                        * List every important class, method, or function with:
                            * Its **signature** (name + parameters)
                            * Its **responsibility** (what it does, in one line)
                            * Its **return type** or side effects
                        * Include constants, enums, and important fields if they are significant.

                        **### ⚙️ Internal Logic & Flow**
                        * Explain the **main execution flow** step-by-step (use numbered list).
                        * Describe any **algorithms** or complex logic (sorting, caching, retries, etc.).
                        * Highlight **conditional branches** or **state transitions** that are important.
                        * If there's a processing pipeline, describe each stage.
                        * Include relevant **code snippets** from the file (2-4 lines max each) to illustrate key logic.

                        **### 🔗 Dependencies & Integration**
                        * **Internal dependencies**: What other files/modules in this project does it import/call?
                        * **External dependencies**: What libraries, frameworks, or APIs does it use?
                        * **Data flow**: What data does it receive? What data does it produce or modify?
                        * **Integration points**: Does it connect to databases, message queues, external APIs, etc.?

                        **### 🛡️ Error Handling & Edge Cases**
                        * How does this file handle errors? (try-catch, Result types, error codes, etc.)
                        * What exceptions can be thrown?
                        * Are there retry mechanisms, fallbacks, or circuit breakers?
                        * What happens with null/empty inputs?

                        **### ⚡ Configuration & Constants**
                        * List any configurable values (timeouts, URLs, feature flags, limits).
                        * Are there hardcoded values that could be externalized?
                        * What environment variables or config properties does it read?

                        ---

                        ### QUALITY RULES
                        * **Be specific**: Reference actual class names, method names, and variable names from the code.
                        * **Explain WHY, not just WHAT**: Don't just say "calls processData()", say "calls processData() to transform raw API responses into domain objects before caching."
                        * **Use code snippets**: Include 2-4 short code snippets from the source to illustrate key patterns.
                        * **Accuracy**: Only document what is actually in the code. Do NOT invent functionality.
                        * **Formatting**: Use proper Markdown with headers, tables, code blocks, and bullet points.
                        * **Length**: Aim for thorough but focused — typically 300-600 words depending on file complexity.
                        * **CRITICAL**: Do NOT wrap the entire output in a markdown code block. Return raw markdown text only.
                        * **Output**: RETURN ONLY THE RAW MARKDOWN. No preamble, no "Here is the documentation".
                                    """,
                fileName, projectContext, fileContent);
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
            for (int i = 0; i < bytesRead; i++)
                if (buffer[i] == 0)
                    return true;
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String toSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir != null) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                logger.warn("Cleanup failed", e);
            }
        }
    }
}