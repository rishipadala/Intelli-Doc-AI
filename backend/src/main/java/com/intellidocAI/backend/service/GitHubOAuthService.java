package com.intellidocAI.backend.service;

import com.intellidocAI.backend.dto.auth.GitHubUserInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@Service
public class GitHubOAuthService {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    /**
     * Exchange the authorization code for a GitHub access token.
     */
    public String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(TOKEN_URL, request, TokenResponse.class);

        if (response.getBody() == null || response.getBody().getAccessToken() == null) {
            log.error("GitHub token exchange failed. Response: {}", response.getBody());
            throw new RuntimeException("Failed to exchange GitHub authorization code for access token");
        }

        log.info("GitHub OAuth token exchange successful");
        return response.getBody().getAccessToken();
    }

    /**
     * Fetch the GitHub user profile using the access token.
     */
    public GitHubUserInfo fetchGitHubUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<GitHubUserInfo> response = restTemplate.exchange(
                USER_URL, HttpMethod.GET, request, GitHubUserInfo.class);

        GitHubUserInfo userInfo = response.getBody();
        if (userInfo == null) {
            throw new RuntimeException("Failed to fetch GitHub user profile");
        }

        // If email is private, fetch from /user/emails endpoint
        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            userInfo.setEmail(fetchPrimaryEmail(accessToken));
        }

        log.info("Fetched GitHub user: {} (ID: {})", userInfo.getLogin(), userInfo.getId());
        return userInfo;
    }

    /**
     * Fetch the primary email address when the user's email is private.
     */
    private String fetchPrimaryEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GitHubEmail[]> response = restTemplate.exchange(
                    EMAILS_URL, HttpMethod.GET, request, GitHubEmail[].class);

            if (response.getBody() != null) {
                return Arrays.stream(response.getBody())
                        .filter(GitHubEmail::isPrimary)
                        .findFirst()
                        .map(GitHubEmail::getEmail)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not fetch GitHub emails: {}", e.getMessage());
        }
        return null;
    }

    // --- Inner DTOs for GitHub API responses ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        private String scope;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitHubEmail {
        private String email;
        private boolean primary;
        private boolean verified;
    }
}
