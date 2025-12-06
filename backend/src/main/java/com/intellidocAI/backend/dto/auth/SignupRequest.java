package com.intellidocAI.backend.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class SignupRequest {
    private String username;
    private String email;
    private String password;
    private List<String> roles; // e.g., ["user", "admin"]
}
