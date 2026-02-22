package com.intellidocAI.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    private String username;
    @Indexed(unique = true, sparse = true)
    private String email;
    private String password;
    private List<String> role; // e.g., "ADMIN", "DEVELOPER"

    // GitHub OAuth fields
    @Indexed(unique = true, sparse = true)
    private String githubId;
    private String avatarUrl;
    @Builder.Default
    private String authProvider = "LOCAL"; // "LOCAL" or "GITHUB"
}
