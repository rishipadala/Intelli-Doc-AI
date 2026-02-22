package com.intellidocAI.backend.service;

import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.repository.DocumentationRepository;
import com.intellidocAI.backend.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service orchestrating the intelligent generation of the project README
 * using analyzed file summaries.
 */
@Service
public class ReadmeGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(ReadmeGenerationService.class);

    @Autowired
    private AiServiceClient aiServiceClient;

    @Autowired
    private DocumentationCacheService docCacheService;

    @Autowired
    private ProgressNotifier progressNotifier;

    @Autowired
    private DocumentationRepository documentationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    /**
     * Executes the README generation pipeline for a specific repository.
     */
    public void generateReadme(Repository repository) {
        repository.setStatus("GENERATING_README");
        repositoryRepository.save(repository);
        progressNotifier.sendStatus(repository.getId(), "GENERATING_README");
        progressNotifier.sendLog(repository.getId(), "INIT", "Starting README generation pipeline...");

        try {
            String projectStructure = repository.getProjectStructure();
            List<Documentation> existingDocs = documentationRepository.findByRepositoryId(repository.getId());

            progressNotifier.sendLog(repository.getId(), "SCAN", "Collecting analyzed file summaries...");
            StringBuilder summariesContext = new StringBuilder();
            for (Documentation doc : existingDocs) {
                if (doc.getFilePath().contains("README_GENERATED"))
                    continue;
                String purpose = extractPurpose(doc.getContent());
                summariesContext.append("- **").append(doc.getFilePath()).append("**: ").append(purpose).append("\n");
            }
            if (summariesContext.length() == 0)
                summariesContext.append("No files were successfully analyzed.");

            // --- README CACHE CHECK ---
            String cachedReadme = docCacheService.getReadmeCache(projectStructure, summariesContext.toString());
            if (cachedReadme != null) {
                logger.info("README Cache Hit! Skipping AI generation.");
                progressNotifier.sendLog(repository.getId(), "CACHE",
                        "README cache hit — using previously generated version");
                saveDocumentation(repository.getId(), "README_GENERATED.md", cachedReadme);
                repository.setStatus("COMPLETED");
                repositoryRepository.save(repository);
                progressNotifier.sendLog(repository.getId(), "COMPLETE", "README generated successfully!");
                progressNotifier.sendStatus(repository.getId(), "COMPLETED");
                return;
            }

            // HIGH-LEVEL PROMPT Built via separated templates class
            String prompt = PromptTemplates.buildReadmePrompt(projectStructure, summariesContext.toString());

            progressNotifier.sendLog(repository.getId(), "AI_GENERATE",
                    "AI is writing the Master README — this may take a moment...");
            String readmeContent = aiServiceClient.callPythonService(prompt).block();

            if (readmeContent != null && !readmeContent.startsWith("Error:")) {
                saveDocumentation(repository.getId(), "README_GENERATED.md", readmeContent);
                progressNotifier.sendLog(repository.getId(), "SAVE", "README saved to project documentation");

                docCacheService.cacheReadme(projectStructure, summariesContext.toString(), readmeContent);
                repository.setStatus("COMPLETED");

                progressNotifier.sendLog(repository.getId(), "COMPLETE", "Master README generated successfully!");
                progressNotifier.sendStatus(repository.getId(), "COMPLETED");

            } else {
                repository.setStatus("FAILED");
                progressNotifier.sendLog(repository.getId(), "ERROR", "AI failed to generate README content");
                progressNotifier.sendStatus(repository.getId(), "FAILED");
            }
            repositoryRepository.save(repository);
        } catch (Exception e) {
            repository.setStatus("FAILED");
            repositoryRepository.save(repository);
            progressNotifier.sendLog(repository.getId(), "ERROR", "README generation failed: " + e.getMessage());
            progressNotifier.sendStatus(repository.getId(), "FAILED");
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
