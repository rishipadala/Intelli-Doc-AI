package com.intellidocAI.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "documentation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documentation {
    @Id
    private String id;
    private String repositoryId; // Links to the Repository
    private String filePath;
    private String content;
}
