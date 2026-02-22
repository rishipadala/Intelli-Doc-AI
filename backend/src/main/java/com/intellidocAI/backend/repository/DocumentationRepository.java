package com.intellidocAI.backend.repository;

import com.intellidocAI.backend.model.Documentation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentationRepository extends MongoRepository<Documentation, String> {
    // We can add a method to find all docs for a specific repo
    List<Documentation> findByRepositoryId(String repositoryId);

    // Find existing doc for upsert (prevent duplicates on re-analysis)
    Optional<Documentation> findByRepositoryIdAndFilePath(String repositoryId, String filePath);

    void deleteByRepositoryId(String repositoryId);

    // Count total docs across multiple repos (for dashboard stats)
    long countByRepositoryIdIn(List<String> repositoryIds);

    // Full-text search across all documentation
    @Query("{ '$text': { '$search': ?0 } }")
    List<Documentation> searchByText(String query, Sort sort);
}
