package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
import com.intellidocAI.backend.exception.ResourceNotFoundException;
import com.intellidocAI.backend.model.Documentation;
import com.intellidocAI.backend.model.Repository;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import com.intellidocAI.backend.service.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> addRepository(@RequestBody Map<String, String> payload) {
        String repoUrl = payload.get("url");

        if (repoUrl == null) {
            throw new IllegalArgumentException("Request must include 'url'.");
        }

        // Get the currently logged-in user's details
        User currentUser = getCurrentUser();

        // Use the ID from the token, NOT the request body
        Repository newRepository = repositoryService.createAndQueueRepository(
                repoUrl,
                currentUser.getId(),
                RepositoryProcessingRequest.ActionType.ANALYZE_CODE);

        // Return 202 ACCEPTED — "I've accepted the job, but it's not done yet."
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(newRepository);
    }

    // Generate README Only
    @PostMapping("/{repositoryId}/generate-readme")
    public ResponseEntity<?> generateReadmeOnly(@PathVariable String repositoryId) {
        repositoryService.queueReadmeGeneration(repositoryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Readme generation queued."));
    }

    /**
     * Polls for the status of a processing job.
     */
    @GetMapping("/{repositoryId}/status")
    public ResponseEntity<?> getRepositoryStatus(@PathVariable String repositoryId) {
        Map<String, String> status = repositoryService.getStatusByRepositoryId(repositoryId);
        return ResponseEntity.ok(status);
    }

    /**
     * Get "My" repositories (uses userId from JWT token).
     */
    @GetMapping("/my-repos")
    public ResponseEntity<List<Repository>> getMyRepositories() {
        User currentUser = getCurrentUser();
        List<Repository> repositories = repositoryService.findRepositoriesByUserId(currentUser.getId());
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/{repositoryId}/documentation")
    public ResponseEntity<List<Documentation>> getDocumentation(@PathVariable String repositoryId) {
        List<Documentation> docs = repositoryService.getDocumentationForRepository(repositoryId);
        return ResponseEntity.ok(docs);
    }

    /**
     * Helper method to extract User from the Security Context
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    // Get the generated README for a repository
    @GetMapping("/{repositoryId}/readme")
    public ResponseEntity<?> getRepositoryReadme(@PathVariable String repositoryId) {
        return repositoryService.getReadmeForRepository(repositoryId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("README not found for repository: " + repositoryId));
    }

    // Delete Repository (ownership verified in service layer)
    @DeleteMapping("/{repositoryId}")
    public ResponseEntity<?> deleteRepository(@PathVariable String repositoryId) {
        User currentUser = getCurrentUser();
        repositoryService.deleteRepository(repositoryId, currentUser.getId());
        return ResponseEntity.ok(Map.of("message", "Repository deleted successfully."));
    }

}
