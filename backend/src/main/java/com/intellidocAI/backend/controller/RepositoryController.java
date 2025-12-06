package com.intellidocAI.backend.controller;


import com.intellidocAI.backend.dto.RepositoryProcessingRequest;
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
            return ResponseEntity.badRequest().body("Request must include 'url' and 'userId'.");
        }

        try {
            // 1. Get the currently logged-in user's details
            User currentUser = getCurrentUser();

            // 2. Use the ID from the token, NOT the request body
            Repository newRepository = repositoryService.createAndQueueRepository(
                    repoUrl,
                    currentUser.getId(),
                    RepositoryProcessingRequest.ActionType.ANALYZE_CODE
            );

            // Return 202 ACCEPTED. This tells the UI "I've accepted
            // the job, but it's not done yet." This is the correct
            // asynchronous response.
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(newRepository);
        } catch (IllegalArgumentException e) {
            // 🔥 CATCH DUPLICATE ERROR HERE
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));

        }catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clone repository: " + e.getMessage());
        }
    }

    // --- 2. 🆕 NEW: Generate README Only ---
    @PostMapping("/{repositoryId}/generate-readme")
    public ResponseEntity<?> generateReadmeOnly(@PathVariable String repositoryId) {
        try {
            repositoryService.queueReadmeGeneration(repositoryId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Readme generation queued."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    /**
     * NEW ENDPOINT: Polls for the status of a processing job.
     */
    @GetMapping("/{repositoryId}/status")
    public ResponseEntity<?> getRepositoryStatus(@PathVariable String repositoryId) {
        try {
            Map<String, String> status = repositoryService.getStatusByRepositoryId(repositoryId);
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * UPDATED: Get "My" repositories.
     * We don't need to pass userID in the URL anymore.
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
        String email = authentication.getName(); // The JWT subject is the email
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // Endpoint: GET /api/repositories/{repositoryId}/readme
    @GetMapping("/{repositoryId}/readme")
    public ResponseEntity<?> getRepositoryReadme(@PathVariable String repositoryId) {
        return repositoryService.getReadmeForRepository(repositoryId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔥 Delete Repository
    @DeleteMapping("/{repositoryId}")
    public ResponseEntity<?> deleteRepository(@PathVariable String repositoryId) {
        try {
            User currentUser = getCurrentUser();
            repositoryService.deleteRepository(repositoryId, currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Repository deleted successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while deleting.");
        }
    }

}
