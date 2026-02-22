package com.intellidocAI.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "documentation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documentation {
    @Id
    private String id;

    @Indexed
    private String repositoryId; // Links to the Repository

    @TextIndexed(weight = 2)
    private String filePath;

    @TextIndexed
    private String content;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
