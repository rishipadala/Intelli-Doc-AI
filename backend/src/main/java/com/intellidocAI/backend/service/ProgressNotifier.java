package com.intellidocAI.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Centralized service for broadcasting real-time progress updates
 * to the frontend via WebSocket (STOMP).
 */
@Service
public class ProgressNotifier {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Sends a progress log entry to the frontend for a specific repository.
     *
     * @param repoId  The repository ID to broadcast to
     * @param step    The pipeline step (e.g., "CLONE", "ARCHITECT", "AI_GENERATE")
     * @param message Human-readable progress message
     */
    public void sendLog(String repoId, String step, String message) {
        messagingTemplate.convertAndSend("/topic/repo/" + repoId, Map.of(
                "type", "PROGRESS_LOG",
                "step", step,
                "message", message,
                "timestamp", LocalDateTime.now().toString()));
    }

    /**
     * Sends a status change event to the frontend for a specific repository.
     *
     * @param repoId The repository ID to broadcast to
     * @param status The new status (e.g., "ANALYZING_CODE", "COMPLETED", "FAILED")
     */
    public void sendStatus(String repoId, String status) {
        messagingTemplate.convertAndSend("/topic/repo/" + repoId, Map.of(
                "type", "STATUS_UPDATE",
                "status", status,
                "lastAnalyzedAt", LocalDateTime.now().toString()));
    }
}
