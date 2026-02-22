package com.intellidocAI.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDTO {
    private String documentationId;
    private String repositoryId;
    private String repositoryName;
    private String filePath;
    private String snippet;
    private Double score;
}
