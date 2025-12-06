package com.intellidocAI.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "repositories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository {
    @Id
    private String id;
    private String name;
    private String url;
    private String userId; // Links to the User who added it
    private String localPath; // Path where it's cloned on the server
    private String status;  // <-- This Line helps to display "status" of our job (e.g., "PENDING", "PROCESSING", "COMPLETED")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastAnalyzedAt;

    // 🆕 NEW FIELD: Store the file tree structure here
    private String projectStructure;
}
