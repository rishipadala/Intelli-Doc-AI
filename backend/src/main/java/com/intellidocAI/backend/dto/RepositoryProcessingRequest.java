package com.intellidocAI.backend.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryProcessingRequest {
    private String repositoryId;
    private String repoUrl;
    private String localPath;
    private String repoName;

    // 🆕 NEW: Define what action to take
    private ActionType actionType;

    public enum ActionType {
        ANALYZE_CODE,
        GENERATE_README
    }
}