package com.intellidocAI.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Stateless utility class for common file system operations
 * used during repository processing.
 */
public final class FileSystemUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemUtils.class);

    private FileSystemUtils() {
        // Utility class — no instantiation
    }

    /**
     * Safely reads a file's content as a UTF-8 string.
     * Returns null if the file cannot be read.
     */
    public static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Recursively deletes a temporary directory and all its contents.
     */
    public static void cleanupTempDir(Path tempDir) {
        if (tempDir != null) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                logger.warn("Cleanup failed", e);
            }
        }
    }
}
