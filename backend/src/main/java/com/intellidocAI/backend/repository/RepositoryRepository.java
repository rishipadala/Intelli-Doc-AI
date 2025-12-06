package com.intellidocAI.backend.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.intellidocAI.backend.model.Repository;
import java.util.List;


@org.springframework.stereotype.Repository
public interface RepositoryRepository extends MongoRepository<Repository, String> {
    List<Repository> findByUserId(String userId);
}
