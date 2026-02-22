package com.intellidocAI.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDTO {
    private int totalRepos;
    private int analyzedRepos;
    private long totalFilesDocumented;
    private String lastAnalysisAt; // ISO timestamp or null
}
