package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.UserDTO;
import com.intellidocAI.backend.mapper.UserMapper;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 🆕 NEW: Get Current Logged-in User Profile
     * Frontend calls this with just the Token to get user details.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUserProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<UserDTO> user = userService.getUserByEmail(email);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get All Users (Admin use case mostly, but kept open for now)
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // Get User By ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get User By Email
    @GetMapping("/email/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        Optional<UserDTO> user = userService.getUserByEmail(email);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update User (SECURED)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody User updatedUserData) {
        // 1. Get the currently logged-in user's email from the Token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalEmail = auth.getName();

        // 2. Find the user we are trying to update
        Optional<User> targetUserOptional = userService.findUserEntityById(id);

        if (targetUserOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User targetUser = targetUserOptional.get();

        // 3. SECURITY CHECK: Ensure the logged-in user owns this account
        // (Unless you add an "ADMIN" role check here later)
        if (!targetUser.getEmail().equals(currentPrincipalEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: You can only update your own profile.");
        }

        // 4. Proceed with update
        targetUser.setUsername(updatedUserData.getUsername());
        targetUser.setEmail(updatedUserData.getEmail());
        // Note: We usually don't update roles here to prevent privilege escalation
        // targetUser.setRole(updatedUserData.getRole());

        // Only update password if a new one is provided and not empty
        if (updatedUserData.getPassword() != null && !updatedUserData.getPassword().isEmpty()) {
            // ideally, you should re-hash the password here using PasswordEncoder
            // But usually password updates are done via a separate secure endpoint
        }

        User savedUser = userService.saveUser(targetUser);
        return ResponseEntity.ok(userMapper.toDto(savedUser));
    }

    // Delete User (SECURED)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        // 1. Get the currently logged-in user's email
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalEmail = auth.getName();

        // 2. Find the user
        Optional<User> targetUserOptional = userService.findUserEntityById(id);

        if (targetUserOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 3. SECURITY CHECK: Ensure user owns the account
        if (!targetUserOptional.get().getEmail().equals(currentPrincipalEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: You can only delete your own account.");
        }

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}