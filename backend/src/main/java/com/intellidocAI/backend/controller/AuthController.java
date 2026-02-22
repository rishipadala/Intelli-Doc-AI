package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.auth.GitHubCodeRequest;
import com.intellidocAI.backend.dto.auth.GitHubUserInfo;
import com.intellidocAI.backend.dto.auth.JwtResponse;
import com.intellidocAI.backend.dto.auth.LoginRequest;
import com.intellidocAI.backend.dto.auth.SignupRequest;
import com.intellidocAI.backend.exception.DuplicateResourceException;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import com.intellidocAI.backend.service.GitHubOAuthService;
import com.intellidocAI.backend.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

        @Autowired
        AuthenticationManager authenticationManager;

        @Autowired
        UserRepository userRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        JwtUtils jwtUtils;

        @Autowired
        GitHubOAuthService gitHubOAuthService;

        @PostMapping("/login")
        public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

                // 1. Authenticate the user (Checks password automatically)
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(),
                                                loginRequest.getPassword()));

                // 2. Set the authentication context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 3. Generate the JWT Token
                String jwt = jwtUtils.generateJwtToken(authentication.getName());

                // 4. Get User Details to send back
                org.springframework.security.core.userdetails.UserDetails userDetails = (org.springframework.security.core.userdetails.UserDetails) authentication
                                .getPrincipal();

                // Convert authorities (roles) to list of strings
                List<String> roles = userDetails.getAuthorities().stream()
                                .map(item -> item.getAuthority())
                                .collect(Collectors.toList());

                // 5. Return the response
                String userId = userRepository.findByEmail(userDetails.getUsername()).get().getId();

                return ResponseEntity.ok(new JwtResponse(jwt,
                                userId,
                                userDetails.getUsername(),
                                userDetails.getUsername(),
                                roles));
        }

        @PostMapping("/signup")
        public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
                // 1. Check if email already exists
                if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
                        throw new DuplicateResourceException("Email is already in use: " + signUpRequest.getEmail());
                }

                // 2. Create new user's account using Builder
                User user = User.builder()
                                .username(signUpRequest.getUsername())
                                .email(signUpRequest.getEmail())
                                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                                .role(signUpRequest.getRoles() != null ? signUpRequest.getRoles()
                                                : Collections.singletonList("USER"))
                                .authProvider("LOCAL")
                                .build();

                userRepository.save(user);

                return ResponseEntity.ok("User registered successfully!");
        }

        @PostMapping("/github")
        public ResponseEntity<?> authenticateWithGitHub(@RequestBody GitHubCodeRequest codeRequest) {
                log.info("GitHub OAuth login initiated");

                // 1. Exchange the authorization code for a GitHub access token
                String accessToken = gitHubOAuthService.exchangeCodeForToken(codeRequest.getCode());

                // 2. Fetch the user's GitHub profile
                GitHubUserInfo ghUser = gitHubOAuthService.fetchGitHubUser(accessToken);
                String githubId = String.valueOf(ghUser.getId());

                // 3. Find or create the user
                User user = userRepository.findByGithubId(githubId)
                                .orElseGet(() -> {
                                        // Check if a user with the same email already exists (link accounts)
                                        if (ghUser.getEmail() != null) {
                                                return userRepository.findByEmail(ghUser.getEmail())
                                                                .map(existingUser -> {
                                                                        // Link GitHub to existing local account
                                                                        existingUser.setGithubId(githubId);
                                                                        existingUser.setAvatarUrl(
                                                                                        ghUser.getAvatarUrl());
                                                                        if (existingUser.getAuthProvider() == null ||
                                                                                        existingUser.getAuthProvider()
                                                                                                        .equals("LOCAL")) {
                                                                                existingUser.setAuthProvider("LOCAL"); // keep
                                                                                                                       // LOCAL
                                                                                                                       // since
                                                                                                                       // they
                                                                                                                       // originally
                                                                                                                       // signed
                                                                                                                       // up
                                                                        }
                                                                        log.info("Linked GitHub account to existing user: {}",
                                                                                        existingUser.getEmail());
                                                                        return userRepository.save(existingUser);
                                                                })
                                                                .orElseGet(() -> createGitHubUser(ghUser, githubId));
                                        }
                                        return createGitHubUser(ghUser, githubId);
                                });

                // 4. Generate JWT (use email as the subject, consistent with normal login)
                String jwt = jwtUtils.generateJwtToken(user.getEmail());

                log.info("GitHub OAuth login successful for user: {}", user.getEmail());

                return ResponseEntity.ok(new JwtResponse(jwt,
                                user.getId(),
                                user.getUsername(),
                                user.getEmail(),
                                user.getRole() != null ? user.getRole() : Collections.singletonList("USER")));
        }

        private User createGitHubUser(GitHubUserInfo ghUser, String githubId) {
                log.info("Creating new user from GitHub: {}", ghUser.getLogin());
                User newUser = User.builder()
                                .username(ghUser.getName() != null ? ghUser.getName() : ghUser.getLogin())
                                .email(ghUser.getEmail() != null ? ghUser.getEmail()
                                                : ghUser.getLogin() + "@github.user")
                                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // random password for
                                                                                                // OAuth users
                                .githubId(githubId)
                                .avatarUrl(ghUser.getAvatarUrl())
                                .authProvider("GITHUB")
                                .role(Collections.singletonList("USER"))
                                .build();
                return userRepository.save(newUser);
        }
}
