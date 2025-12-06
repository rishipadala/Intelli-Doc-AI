package com.intellidocAI.backend.repository;

import com.intellidocAI.backend.model.Documentation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentationRepository extends MongoRepository<Documentation, String> {
    // We can add a method to find all docs for a specific repo
    List<Documentation> findByRepositoryId(String repositoryId);

    void deleteByRepositoryId(String repositoryId);
}
