package com.intellidocAI.backend.service;

import com.intellidocAI.backend.config.KafkaTopicConfig;
import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
import com.intellidocAI.backend.dto.DashboardStatsDTO;
import com.intellidocAI.backend.dto.SearchResultDTO;
import com.intellidocAI.backend.exception.DuplicateResourceException;
import com.intellidocAI.backend.exception.ForbiddenAccessException;
import com.intellidocAI.backend.exception.ResourceNotFoundException;
import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.repository.DocumentationRepository;
import com.intellidocAI.backend.repository.RepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
    public Repository createAndQueueRepository(String repoUrl, String userId,
            RepositoryProcessingRequest.ActionType actionType) {

        // 1. NORMALIZE URL (Remove .git and trailing slash)
        String cleanUrl = normalizeUrl(repoUrl);

        // 2. ROBUST DUPLICATE CHECK
        List<Repository> userRepos = repositoryRepository.findByUserId(userId);
        boolean alreadyExists = userRepos.stream()
                .anyMatch(r -> normalizeUrl(r.getUrl()).equalsIgnoreCase(cleanUrl));

        if (alreadyExists) {
            throw new DuplicateResourceException("Repository already exists in your dashboard.");
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
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repositoryId));

        RepositoryProcessingRequest request = new RepositoryProcessingRequest(
                repository.getId(),
                repository.getUrl(),
                repository.getLocalPath(),
                repository.getName(),
                RepositoryProcessingRequest.ActionType.GENERATE_README);

        kafkaTemplate.send(KafkaTopicConfig.REPO_PROCESSING_TOPIC, request);
        log.info("QUEUED README GENERATION for: {}", repository.getName());
    }

    /**
     * NEW METHOD: Gets the current status of a repository.
     */
    public Map<String, String> getStatusByRepositoryId(String repositoryId) {
        // Find the repository by its ID
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repositoryId));

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

    // ✏️ UPDATE README: Edit the generated README content
    public Documentation updateReadmeContent(String repositoryId, String newContent) {
        Documentation readme = documentationRepository.findByRepositoryId(repositoryId)
                .stream()
                .filter(doc -> doc.getFilePath().equals("README_GENERATED.md"))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("README not found for repository: " + repositoryId));
        readme.setContent(newContent);
        return documentationRepository.save(readme);
    }

    // Helper to strip .git and /
    private String normalizeUrl(String url) {
        if (url == null)
            return "";
        String clean = url.trim();
        if (clean.endsWith("/"))
            clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith(".git"))
            clean = clean.substring(0, clean.length() - 4);
        return clean;
    }

    // 🔥 NEW METHOD: Cascading Delete
    @Transactional
    public void deleteRepository(String repositoryId, String userId) {
        // 1. Find Repo
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repositoryId));

        // 2. Security Check: Ensure the user actually owns this repo!
        if (!repository.getUserId().equals(userId)) {
            throw new ForbiddenAccessException("Access Denied: You cannot delete a repository you do not own.");
        }

        // 3. Delete all associated documentation first (Clean up)
        documentationRepository.deleteByRepositoryId(repositoryId);

        // 4. Delete the repository metadata
        repositoryRepository.deleteById(repositoryId);

        log.info("Deleted repository: {}", repository.getName());
    }

    // 🔍 SEARCH: Full-text search across all user's documentation
    public List<SearchResultDTO> searchDocumentation(String query, String userId) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Get all repos owned by this user → build a lookup map
        List<Repository> userRepos = repositoryRepository.findByUserId(userId);
        Map<String, String> repoIdToName = userRepos.stream()
                .collect(Collectors.toMap(Repository::getId, Repository::getName));
        Set<String> userRepoIds = repoIdToName.keySet();

        // 2. Run MongoDB text search (sorted by text score descending)
        Sort sortByScore = Sort.by(Sort.Order.desc("score"));
        List<Documentation> allResults = documentationRepository.searchByText(query.trim(), sortByScore);

        // 3. Filter to only user's repos, map to DTO, cap at 50
        String queryLower = query.trim().toLowerCase();
        return allResults.stream()
                .filter(doc -> userRepoIds.contains(doc.getRepositoryId()))
                .limit(50)
                .map(doc -> SearchResultDTO.builder()
                        .documentationId(doc.getId())
                        .repositoryId(doc.getRepositoryId())
                        .repositoryName(repoIdToName.getOrDefault(doc.getRepositoryId(), "Unknown"))
                        .filePath(doc.getFilePath())
                        .snippet(extractSnippet(doc.getContent(), queryLower, 200))
                        .score(0.0) // Score from $text is available via $meta, simplified here
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Extract a snippet of ~maxLen chars around the first occurrence of the query.
     */
    private String extractSnippet(String content, String query, int maxLen) {
        if (content == null || content.isEmpty())
            return "";

        String contentLower = content.toLowerCase();
        int idx = contentLower.indexOf(query);

        if (idx == -1) {
            // Query not found literally (MongoDB tokenizes differently) — return start
            return content.substring(0, Math.min(content.length(), maxLen)) + (content.length() > maxLen ? "..." : "");
        }

        // Center the snippet around the match
        int start = Math.max(0, idx - maxLen / 3);
        int end = Math.min(content.length(), start + maxLen);

        String snippet = content.substring(start, end);
        if (start > 0)
            snippet = "..." + snippet;
        if (end < content.length())
            snippet = snippet + "...";

        return snippet;
    }

    // 📊 DASHBOARD STATS: Aggregate stats for the user's dashboard
    public DashboardStatsDTO getDashboardStats(String userId) {
        List<Repository> userRepos = repositoryRepository.findByUserId(userId);

        int totalRepos = userRepos.size();
        int analyzedRepos = (int) userRepos.stream()
                .filter(r -> "ANALYSIS_COMPLETED".equals(r.getStatus()) || "COMPLETED".equals(r.getStatus()))
                .count();

        // Count total documented files across all user repos
        List<String> repoIds = userRepos.stream().map(Repository::getId).collect(Collectors.toList());
        long totalFiles = repoIds.isEmpty() ? 0 : documentationRepository.countByRepositoryIdIn(repoIds);

        // Find the most recent analysis timestamp
        String lastAnalysis = userRepos.stream()
                .map(Repository::getLastAnalyzedAt)
                .filter(Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .map(Object::toString)
                .orElse(null);

        return DashboardStatsDTO.builder()
                .totalRepos(totalRepos)
                .analyzedRepos(analyzedRepos)
                .totalFilesDocumented(totalFiles)
                .lastAnalysisAt(lastAnalysis)
                .build();
    }
}