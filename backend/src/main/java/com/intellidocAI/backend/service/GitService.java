package com.intellidocAI.backend.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    /**
     * Clones a repository into a specific directory.
     * * @param repoUrl The URL of the git repository.
     * @param destinationPath The full local path where the repo should be cloned.
     */
    public void cloneRepository(String repoUrl, String destinationPath) throws GitAPIException {
        File localDir = new File(destinationPath);

        logger.info("Cloning {} into {}", repoUrl, destinationPath);

        // We wrap the clone command in a try-with-resources block.
        // This guarantees the 'git' object is closed, releasing file locks.
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(localDir)
                .setDepth(1) // Shallow clone (faster)
                .call()) {

            logger.info("Completed shallow clone of {}", repoUrl);
        } catch (GitAPIException e) {
            logger.error("Failed to clone repository: {}", e.getMessage());
            throw e; // Re-throw to let the Worker handle the error
        }
    }
}