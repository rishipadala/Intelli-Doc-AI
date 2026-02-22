package com.intellidocAI.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUserInfo {
    private Long id;
    private String login;
    private String email;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String name;
}
