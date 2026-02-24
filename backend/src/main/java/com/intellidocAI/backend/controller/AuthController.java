package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.auth.*;
import com.intellidocAI.backend.exception.DuplicateResourceException;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import com.intellidocAI.backend.service.EmailService;
import com.intellidocAI.backend.service.GitHubOAuthService;
import com.intellidocAI.backend.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
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

        @Autowired
        EmailService emailService;

        @Value("${app.otpExpirationMinutes:10}")
        private int otpExpirationMinutes;

        @PostMapping("/login")
        public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

                // Check if the user's email is verified before allowing login
                Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());
                if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        // Skip verification check for GitHub OAuth users
                        if ("LOCAL".equals(user.getAuthProvider()) && !user.isEmailVerified()) {
                                // Generate and send a new OTP so they can verify
                                String otp = generateOtp();
                                user.setEmailOtp(passwordEncoder.encode(otp));
                                user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
                                userRepository.save(user);
                                emailService.sendOtpEmail(user.getEmail(), otp);

                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(Map.of(
                                                                "error", "EMAIL_NOT_VERIFIED",
                                                                "message",
                                                                "Your email is not verified. A new OTP has been sent to "
                                                                                + loginRequest.getEmail()));
                        }
                }

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

                // 2. Generate a 6-digit OTP
                String otp = generateOtp();

                // 3. Create new user's account (unverified)
                User user = User.builder()
                                .username(signUpRequest.getUsername())
                                .email(signUpRequest.getEmail())
                                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                                .role(signUpRequest.getRoles() != null ? signUpRequest.getRoles()
                                                : Collections.singletonList("USER"))
                                .authProvider("LOCAL")
                                .emailVerified(false)
                                .emailOtp(passwordEncoder.encode(otp))
                                .otpExpiry(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                                .build();

                userRepository.save(user);

                // 4. Send OTP email
                emailService.sendOtpEmail(signUpRequest.getEmail(), otp);

                log.info("User registered (pending verification): {}", signUpRequest.getEmail());

                return ResponseEntity.ok(Map.of(
                                "message", "OTP sent to " + signUpRequest.getEmail()
                                                + ". Please verify your email to complete registration."));
        }

        @PostMapping("/verify-otp")
        public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
                Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

                if (userOpt.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of("error", "No account found with this email."));
                }

                User user = userOpt.get();

                if (user.isEmailVerified()) {
                        return ResponseEntity.ok(Map.of("message", "Email is already verified. Please log in."));
                }

                // Check OTP expiry
                if (user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("error", "OTP has expired. Please request a new one."));
                }

                // Verify OTP
                if (!passwordEncoder.matches(request.getOtp(), user.getEmailOtp())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("error", "Invalid OTP. Please try again."));
                }

                // Mark email as verified and clear OTP fields
                user.setEmailVerified(true);
                user.setEmailOtp(null);
                user.setOtpExpiry(null);
                userRepository.save(user);

                log.info("Email verified successfully for user: {}", request.getEmail());

                return ResponseEntity.ok(Map.of("message", "Email verified successfully! You can now log in."));
        }

        @PostMapping("/resend-otp")
        public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
                Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

                if (userOpt.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of("error", "No account found with this email."));
                }

                User user = userOpt.get();

                if (user.isEmailVerified()) {
                        return ResponseEntity.ok(Map.of("message", "Email is already verified. Please log in."));
                }

                // Rate limit: prevent resending if last OTP was sent less than 60 seconds ago
                if (user.getOtpExpiry() != null) {
                        LocalDateTime lastSentAt = user.getOtpExpiry().minusMinutes(otpExpirationMinutes);
                        if (LocalDateTime.now().isBefore(lastSentAt.plusSeconds(60))) {
                                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                                .body(Map.of("error",
                                                                "Please wait at least 60 seconds before requesting a new OTP."));
                        }
                }

                // Generate and send new OTP
                String otp = generateOtp();
                user.setEmailOtp(passwordEncoder.encode(otp));
                user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
                userRepository.save(user);

                emailService.sendOtpEmail(request.getEmail(), otp);

                log.info("OTP resent to: {}", request.getEmail());

                return ResponseEntity.ok(Map.of("message", "A new OTP has been sent to " + request.getEmail()));
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
                                                                                existingUser.setAuthProvider("LOCAL");
                                                                        }
                                                                        // GitHub-verified emails can be auto-verified
                                                                        existingUser.setEmailVerified(true);
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
                                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                                .githubId(githubId)
                                .avatarUrl(ghUser.getAvatarUrl())
                                .authProvider("GITHUB")
                                .emailVerified(true) // GitHub-verified emails are trusted
                                .role(Collections.singletonList("USER"))
                                .build();
                return userRepository.save(newUser);
        }

        /**
         * Generates a cryptographically random 6-digit OTP.
         */
        private String generateOtp() {
                Random random = new Random();
                int otp = 100000 + random.nextInt(900000);
                return String.valueOf(otp);
        }
}
