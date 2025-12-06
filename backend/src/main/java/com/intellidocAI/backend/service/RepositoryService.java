package com.intellidocAI.backend.service;

import com.intellidocAI.backend.config.KafkaTopicConfig;
import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.repository.DocumentationRepository;
import com.intellidocAI.backend.repository.RepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
public class RepositoryService {

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private DocumentationRepository documentationRepository;

    @Autowired
    private KafkaTemplate<String, RepositoryProcessingRequest> kafkaTemplate;

    @Value("${repo.clone.path}")
    private String reposDirectory;


    // 1. Queue Code Analysis
    public Repository createAndQueueRepository(String repoUrl, String userId, RepositoryProcessingRequest.ActionType actionType) {

        // 1. NORMALIZE URL (Remove .git and trailing slash)
        String cleanUrl = normalizeUrl(repoUrl);

        // 2. ROBUST DUPLICATE CHECK
        List<Repository> userRepos = repositoryRepository.findByUserId(userId);
        boolean alreadyExists = userRepos.stream()
                .anyMatch(r -> normalizeUrl(r.getUrl()).equalsIgnoreCase(cleanUrl));

        if (alreadyExists) {
            throw new IllegalArgumentException("Repository already exists in your dashboard.");
        }

        Repository repository = new Repository();
        repository.setUrl(repoUrl);
        repository.setUserId(userId);
        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replace(".git", "");
        repository.setName(repoName);
        repository.setLocalPath(Paths.get(reposDirectory, repoName).toString());
        repository.setStatus("QUEUED");

        Repository savedRepository = repositoryRepository.save(repository);

        RepositoryProcessingRequest request = new RepositoryProcessingRequest(
                savedRepository.getId(),
                savedRepository.getUrl(),
                savedRepository.getLocalPath(),
                savedRepository.getName(),
                actionType // Pass the action!
        );

        kafkaTemplate.send(KafkaTopicConfig.REPO_PROCESSING_TOPIC, request);
        log.info("QUEUED ANALYSIS for: {}", savedRepository.getName());
        return savedRepository;
    }

    // 2. Queue Readme Generation
    public void queueReadmeGeneration(String repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RuntimeException("Repo not found"));

        RepositoryProcessingRequest request = new RepositoryProcessingRequest(
                repository.getId(),
                repository.getUrl(),
                repository.getLocalPath(),
                repository.getName(),
                RepositoryProcessingRequest.ActionType.GENERATE_README
        );

        kafkaTemplate.send(KafkaTopicConfig.REPO_PROCESSING_TOPIC, request);
        log.info("QUEUED README GENERATION for: {}", repository.getName());
    }

    /**
     * NEW METHOD: Gets the current status of a repository.
     */
    public Map<String, String> getStatusByRepositoryId(String repositoryId) {
        // Find the repository by its ID
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repositoryId));

        // Return the status in a JSON-friendly Map
        // (Make sure you added the 'status' field to your Repository.java model)
        return Map.of("status", repository.getStatus());
    }

    public List<Repository> findRepositoriesByUserId(String userId) {
        return repositoryRepository.findByUserId(userId);
    }

    public List<Documentation> getDocumentationForRepository(String repositoryId) {
        return documentationRepository.findByRepositoryId(repositoryId);
    }

    // 🔥 NEW METHOD: Fetch only the Readme file
    public Optional<Documentation> getReadmeForRepository(String repositoryId) {
        List<Documentation> docs = documentationRepository.findByRepositoryId(repositoryId);
        return docs.stream()
                .filter(doc -> doc.getFilePath().equals("README_GENERATED.md"))
                .findFirst();
    }

    // Helper to strip .git and /
    private String normalizeUrl(String url) {
        if (url == null) return "";
        String clean = url.trim();
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith(".git")) clean = clean.substring(0, clean.length() - 4);
        return clean;
    }

    // 🔥 NEW METHOD: Cascading Delete
    @Transactional
    public void deleteRepository(String repositoryId, String userId) {
        // 1. Find Repo
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        // 2. Security Check: Ensure the user actually owns this repo!
        if (!repository.getUserId().equals(userId)) {
            throw new RuntimeException("Access Denied: You cannot delete a repository you do not own.");
        }

        // 3. Delete all associated documentation first (Clean up)
        documentationRepository.deleteByRepositoryId(repositoryId);

        // 4. Delete the repository metadata
        repositoryRepository.deleteById(repositoryId);

        log.info("Deleted repository: {}", repository.getName());
    }


}