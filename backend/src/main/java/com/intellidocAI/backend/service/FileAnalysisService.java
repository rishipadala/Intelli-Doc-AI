package com.intellidocAI.backend.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service responsible for scanning a project directory, calculating heuristics,
 * filtering out junk files, and providing fallback file selections when AI
 * fails.
 */
@Service
public class FileAnalysisService {

    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024; // 100KB limit per file
    public static final int MAX_FILES_TO_SELECT = 8; // Max files to analyze per repo

    /**
     * The "Safety Net": If AI Architect fails, use our old smart scoring logic
     */
    public List<Path> fallbackSelection(Path tempDir) throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isValidCodeFile)
                    .sorted(Comparator.comparingInt(this::calculateFileScore).reversed())
                    .limit(MAX_FILES_TO_SELECT)
                    .collect(Collectors.toList());
        }
    }

    public String generateProjectContext(Path rootDir) {
        StringBuilder sb = new StringBuilder("Project Structure:\n");
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isNotJunk)
                    .sorted((p1, p2) -> {
                        String f1 = p1.getFileName().toString().toLowerCase();
                        String f2 = p2.getFileName().toString().toLowerCase();
                        if (isVitalConfig(f1))
                            return -1;
                        if (isVitalConfig(f2))
                            return 1;
                        return p1.compareTo(p2);
                    })
                    .limit(300)
                    .forEach(path -> sb.append(rootDir.relativize(path).toString().replace("\\", "/")).append("\n"));
        } catch (IOException e) {
            return "";
        }
        return sb.toString();
    }

    // Helper for the sorting logic
    private boolean isVitalConfig(String filename) {
        return filename.equals("pom.xml") ||
                filename.equals("build.gradle") ||
                filename.equals("dockerfile") ||
                filename.equals("requirements.txt") ||
                filename.equals("package.json");
    }

    // Basic filter for Tree Generation (Don't confuse Architect with node_modules)
    private boolean isNotJunk(Path path) {
        String abs = path.toString();
        if (abs.contains(".git") || abs.contains("node_modules") || abs.contains("target")
                || abs.contains("__pycache__"))
            return false;
        String name = path.getFileName().toString().toLowerCase();
        return !name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".class") && !name.endsWith(".jar");
    }

    // Strict filter for Fallback Logic
    private boolean isValidCodeFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        String absolutePath = path.toString();

        // 1. HARD BLOCKLIST (Directories)
        if (absolutePath.contains(File.separator + ".git") ||
                absolutePath.contains(File.separator + "node_modules") ||
                absolutePath.contains(File.separator + "target") ||
                absolutePath.contains(File.separator + "build") ||
                absolutePath.contains(File.separator + "dist") ||
                absolutePath.contains(File.separator + "bin") ||
                absolutePath.contains(File.separator + "obj") ||
                absolutePath.contains(File.separator + "__pycache__") ||
                absolutePath.contains(File.separator + ".idea") ||
                absolutePath.contains(File.separator + ".vscode")) {
            return false;
        }

        // 2. HARD BLOCKLIST (Extensions)
        List<String> blockedExts = Arrays.asList(
                ".sh", ".bat", ".cmd", ".ps1", "dockerfile", "makefile", "jenkinsfile",
                ".yml", ".yaml", ".xml", ".json", ".conf", ".properties", ".ini", ".env",
                ".txt", ".md", ".csv", ".sql", ".svg", ".css", ".html",
                ".iml", ".class", ".jar", ".war", ".exe", ".dll", ".so",
                ".suo", ".sln", ".user", ".lock", ".log");

        if (blockedExts.stream().anyMatch(filename::endsWith))
            return false;

        // 3. SIZE CHECK
        try {
            if (Files.size(path) > MAX_FILE_SIZE_BYTES)
                return false;
        } catch (IOException e) {
            return false;
        }

        return !isBinaryFile(path);
    }

    private int calculateFileScore(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.matches("^(app|main|server|index|application)\\.(java|py|js|ts)$"))
            return 100;
        return 10;
    }

    private boolean isBinaryFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[512];
            int bytesRead = in.read(buffer);
            for (int i = 0; i < bytesRead; i++)
                if (buffer[i] == 0)
                    return true;
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
