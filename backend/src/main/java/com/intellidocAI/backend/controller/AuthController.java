package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.auth.JwtResponse;
import com.intellidocAI.backend.dto.auth.LoginRequest;
import com.intellidocAI.backend.dto.auth.SignupRequest;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import com.intellidocAI.backend.utils.JwtUtils;
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
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
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

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        // 1. Authenticate the user (Checks password automatically)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        // 2. Set the authentication context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Generate the JWT Token
        String jwt = jwtUtils.generateJwtToken(authentication.getName());

        // 4. Get User Details to send back
        org.springframework.security.core.userdetails.UserDetails userDetails =
                (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();

        // Convert authorities (roles) to list of strings
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // 5. Return the response
        // Note: We fetch the ID from DB because standard UserDetails doesn't have it
        String userId = userRepository.findByEmail(userDetails.getUsername()).get().getId();

        return ResponseEntity.ok(new JwtResponse(jwt,
                userId,
                userDetails.getUsername(),
                userDetails.getUsername(), // In our logic, email is the username
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signUpRequest) {
        // 1. Check if email already exists
        if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Email is already in use!");
        }

        // 2. Create new user's account using Builder
        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(passwordEncoder.encode(signUpRequest.getPassword())) // Hash the password!
                .role(signUpRequest.getRoles() != null ? signUpRequest.getRoles() : Collections.singletonList("USER"))
                .build();

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }
}