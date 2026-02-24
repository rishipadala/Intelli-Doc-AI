package com.intellidocAI.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

/**
 * Service handling caching of AI responses to Redis.
 */
@Service
public class DocumentationCacheService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentationCacheService.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    // --- Architect Cache ---
    public String getArchitectCacheKey(String projectContext) {
        return "architect:" + toSha256(projectContext);
    }

    public List<String> getArchitectCache(String projectContext) {
        String key = getArchitectCacheKey(projectContext);
        String cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            try {
                return objectMapper.readValue(cachedValue, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                logger.warn("Failed to parse cached architect response, re-fetching.");
            }
        }
        return null;
    }

    public void cacheArchitectSelection(String projectContext, List<String> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty())
            return;
        String key = getArchitectCacheKey(projectContext);
        try {
            String json = objectMapper.writeValueAsString(selectedFiles);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(1));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to cache architect response");
        }
    }

    // --- Doc Cache ---
    public String getDocCacheKey(String fileContent) {
        return "doc:" + toSha256(fileContent);
    }

    public String getDocCache(String fileContent) {
        return redisTemplate.opsForValue().get(getDocCacheKey(fileContent));
    }

    public void cacheDoc(String fileContent, String documentation) {
        if (documentation == null || documentation.startsWith("Error:"))
            return;
        redisTemplate.opsForValue().set(getDocCacheKey(fileContent), documentation, Duration.ofDays(1));
    }

    // --- Readme Cache ---
    public String getReadmeCacheKey(String projectStructure, String summariesContext) {
        return "readme:" + toSha256(projectStructure + summariesContext);
    }

    public String getReadmeCache(String projectStructure, String summariesContext) {
        return redisTemplate.opsForValue().get(getReadmeCacheKey(projectStructure, summariesContext));
    }

    public void cacheReadme(String projectStructure, String summariesContext, String readmeContent) {
        if (readmeContent == null || readmeContent.startsWith("Error:"))
            return;
        redisTemplate.opsForValue().set(getReadmeCacheKey(projectStructure, summariesContext), readmeContent,
                Duration.ofDays(1));
    }
}
