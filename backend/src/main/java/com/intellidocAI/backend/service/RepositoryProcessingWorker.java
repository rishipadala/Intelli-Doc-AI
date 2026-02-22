package com.intellidocAI.backend.service;

import com.intellidocAI.backend.config.KafkaTopicConfig;
import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.repository.DocumentationRepository;
import com.intellidocAI.backend.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrator service that coordinates the entire repository processing
 * pipeline.
 * It delegates specific tasks to focused services based on the Single
 * Responsibility Principle.
 */
@Service
public class RepositoryProcessingWorker {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryProcessingWorker.class);
    private static final int BATCH_SIZE = 4; // Files per batch API call

    @Autowired
    private GitService gitService;
    @Autowired
    private RepositoryRepository repositoryRepository;
    @Autowired
    private DocumentationRepository documentationRepository;

    @Autowired
    private FileAnalysisService fileAnalysisService;
    @Autowired
    private AiServiceClient aiServiceClient;
    @Autowired
    private DocumentationCacheService docCacheService;
    @Autowired
    private ReadmeGenerationService readmeGenerationService;
    @Autowired
    private ProgressNotifier progressNotifier;

    @KafkaListener(topics = KafkaTopicConfig.REPO_PROCESSING_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void processRepository(RepositoryProcessingRequest request) {
        Repository repository = repositoryRepository.findById(request.getRepositoryId()).orElse(null);
        if (repository == null)
            return;

        try {
            if (request.getActionType() == RepositoryProcessingRequest.ActionType.ANALYZE_CODE) {
                performCodeAnalysis(repository, request);
            } else if (request.getActionType() == RepositoryProcessingRequest.ActionType.GENERATE_README) {
                readmeGenerationService.generateReadme(repository);
            }
        } catch (Exception e) {
            logger.error("CRITICAL FAILURE for {}", repository.getName(), e);
            repository.setStatus("FAILED");
            repositoryRepository.save(repository);
            progressNotifier.sendLog(repository.getId(), "ERROR", "Processing failed: " + e.getMessage());
            progressNotifier.sendStatus(repository.getId(), "FAILED");
        }
    }

    // ============================================================================================
    // 🧠 ACTION 1: AGENTIC CODE ANALYSIS (The Architect Workflow)
    // ============================================================================================
    private void performCodeAnalysis(Repository repository, RepositoryProcessingRequest request) {
        repository.setStatus("ANALYZING_CODE");
        repositoryRepository.save(repository);
        progressNotifier.sendStatus(repository.getId(), "ANALYZING_CODE");
        progressNotifier.sendLog(repository.getId(), "INIT", "Starting code analysis pipeline...");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("intellidoc_" + request.getRepositoryId());
            progressNotifier.sendLog(repository.getId(), "CLONE", "Cloning repository from GitHub...");
            gitService.cloneRepository(request.getRepoUrl(), tempDir.toAbsolutePath().toString());
            progressNotifier.sendLog(repository.getId(), "CLONE", "Repository cloned successfully");

            progressNotifier.sendLog(repository.getId(), "SCAN", "Scanning project structure and file tree...");
            String projectContext = fileAnalysisService.generateProjectContext(tempDir);
            repository.setProjectStructure(projectContext);
            progressNotifier.sendLog(repository.getId(), "SCAN", "Project structure mapped");

            // --- 1. AI ARCHITECT FILE SELECTION (Cached, Heuristic as Fallback) ---
            List<Path> filesToProcess = new ArrayList<>();
            Path finalTempDir = tempDir;

            List<String> selectedFiles = docCacheService.getArchitectCache(projectContext);

            if (selectedFiles != null) {
                logger.info("Architect Cache Hit! Skipping AI consultation.");
                progressNotifier.sendLog(repository.getId(), "ARCHITECT",
                        "AI Architect cache hit — using saved file selection");
            } else {
                logger.info("Consulting AI Architect for intelligent file selection...");
                progressNotifier.sendLog(repository.getId(), "ARCHITECT",
                        "Consulting AI Architect for intelligent file selection...");
                selectedFiles = aiServiceClient.callAiFileSelector(projectContext).block();

                // Cache the AI selection for 2 days
                docCacheService.cacheArchitectSelection(projectContext, selectedFiles);
            }

            // Map AI-selected file paths to actual Path objects
            if (selectedFiles != null && !selectedFiles.isEmpty()) {
                logger.info("AI Architect selected {} files.", selectedFiles.size());
                progressNotifier.sendLog(repository.getId(), "ARCHITECT",
                        "AI selected " + selectedFiles.size() + " key files for deep analysis");
                try (Stream<Path> paths = Files.walk(tempDir)) {
                    List<String> finalSelectedFiles = selectedFiles;
                    filesToProcess = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                String relPath = finalTempDir.relativize(path).toString().replace("\\", "/");
                                return finalSelectedFiles.stream().anyMatch(f -> relPath.endsWith(f));
                            })
                            .limit(FileAnalysisService.MAX_FILES_TO_SELECT)
                            .collect(Collectors.toList());
                }
            }

            // Fallback to heuristic ONLY if AI Architect returned nothing
            if (filesToProcess.isEmpty()) {
                logger.warn("AI Architect returned no files. Falling back to heuristic selection.");
                progressNotifier.sendLog(repository.getId(), "ARCHITECT",
                        "Using smart heuristic file selection as fallback...");
                filesToProcess = fileAnalysisService.fallbackSelection(tempDir);
            }

            logger.info("Processing {} files in batches of {}...", filesToProcess.size(), BATCH_SIZE);
            progressNotifier.sendLog(repository.getId(), "PROCESS",
                    "Preparing to analyze " + filesToProcess.size() + " files in batches of " + BATCH_SIZE);

            // --- 2. Separate cached vs uncached files ---
            List<Map<String, String>> uncachedFiles = new ArrayList<>();
            for (Path filePath : filesToProcess) {
                String relativePath = finalTempDir.relativize(filePath).toString().replace("\\", "/");
                String fileContent = FileSystemUtils.readString(filePath);
                if (fileContent == null || fileContent.isBlank())
                    continue;

                String cachedDoc = docCacheService.getDocCache(fileContent);

                if (cachedDoc != null) {
                    logger.info("Cache Hit for: {}", relativePath);
                    progressNotifier.sendLog(repository.getId(), "CACHE", "Cache hit for: " + relativePath);
                    saveDocumentation(repository.getId(), relativePath, cachedDoc);
                } else {
                    uncachedFiles.add(Map.of(
                            "path", relativePath,
                            "content", fileContent));
                }
            }

            // --- 3. BATCH Process uncached files (dramatically fewer API calls) ---
            if (!uncachedFiles.isEmpty()) {
                logger.info("{} files need AI generation (in {} batch(es))",
                        uncachedFiles.size(), (int) Math.ceil((double) uncachedFiles.size() / BATCH_SIZE));

                for (int i = 0; i < uncachedFiles.size(); i += BATCH_SIZE) {
                    List<Map<String, String>> batch = uncachedFiles.subList(
                            i, Math.min(i + BATCH_SIZE, uncachedFiles.size()));

                    int batchNum = (i / BATCH_SIZE) + 1;
                    int totalBatches = (int) Math.ceil((double) uncachedFiles.size() / BATCH_SIZE);
                    logger.info("Processing batch {}/{} ({} files)", batchNum, totalBatches, batch.size());

                    String batchFiles = batch.stream().map(f -> f.get("path")).collect(Collectors.joining(", "));
                    progressNotifier.sendLog(repository.getId(), "AI_GENERATE",
                            "Generating docs — batch " + batchNum + "/" + totalBatches + " (" + batchFiles + ")");

                    List<Map<String, String>> batchResults = aiServiceClient
                            .callBatchPythonService(batch, projectContext).block();

                    if (batchResults != null) {
                        for (Map<String, String> result : batchResults) {
                            String path = result.get("path");
                            String doc = result.get("documentation");
                            if (path != null && doc != null && !doc.startsWith("Error:")) {
                                saveDocumentation(repository.getId(), path, doc);
                                progressNotifier.sendLog(repository.getId(), "SAVE", "Documentation saved: " + path);
                                // Cache each file's doc individually
                                String originalContent = batch.stream()
                                        .filter(f -> f.get("path").equals(path))
                                        .map(f -> f.get("content"))
                                        .findFirst().orElse(null);
                                if (originalContent != null) {
                                    docCacheService.cacheDoc(originalContent, doc);
                                }
                            } else {
                                logger.warn("AI Failed for file in batch: {} (Skipping save)", path);
                            }
                        }
                    }
                }
            }

            repository.setStatus("ANALYSIS_COMPLETED");
            repository.setLastAnalyzedAt(LocalDateTime.now());
            repositoryRepository.save(repository);

            progressNotifier.sendLog(repository.getId(), "COMPLETE",
                    "Code analysis complete! Documentation ready to view.");
            progressNotifier.sendStatus(repository.getId(), "ANALYSIS_COMPLETED");

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FileSystemUtils.cleanupTempDir(tempDir);
        }
    }

    private void saveDocumentation(String repositoryId, String filePath, String content) {
        Documentation doc = documentationRepository
                .findByRepositoryIdAndFilePath(repositoryId, filePath)
                .orElse(new Documentation());

        doc.setRepositoryId(repositoryId);
        doc.setFilePath(filePath);
        doc.setContent(content);
        documentationRepository.save(doc);
        logger.info("Saved docs for: {}", filePath);
    }
}