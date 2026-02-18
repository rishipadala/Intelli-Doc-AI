package com.intellidocAI.backend.controller;

import com.intellidocAI.backend.dto.UserDTO;
import com.intellidocAI.backend.exception.ForbiddenAccessException;
import com.intellidocAI.backend.exception.ResourceNotFoundException;
import com.intellidocAI.backend.mapper.UserMapper;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    /**
     * Get Current Logged-in User Profile.
     * Frontend calls this with just the Token to get user details.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUserProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserDTO user = userService.getUserByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return ResponseEntity.ok(user);
    }

    // Get All Users
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // Get User By ID
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String id) {
        UserDTO user = userService.getUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return ResponseEntity.ok(user);
    }

    // Get User By Email
    @GetMapping("/email/{email}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        UserDTO user = userService.getUserByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return ResponseEntity.ok(user);
    }

    // Update User (SECURED)
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable String id, @RequestBody User updatedUserData) {
        // 1. Get the currently logged-in user's email from the Token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalEmail = auth.getName();

        // 2. Find the user we are trying to update
        User targetUser = userService.findUserEntityById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // 3. SECURITY CHECK: Ensure the logged-in user owns this account
        if (!targetUser.getEmail().equals(currentPrincipalEmail)) {
            throw new ForbiddenAccessException("Access Denied: You can only update your own profile.");
        }

        // 4. Proceed with update
        targetUser.setUsername(updatedUserData.getUsername());
        targetUser.setEmail(updatedUserData.getEmail());

        // Only update password if a new one is provided and not empty
        if (updatedUserData.getPassword() != null && !updatedUserData.getPassword().isEmpty()) {
            // ideally, you should re-hash the password here using PasswordEncoder
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
        User targetUser = userService.findUserEntityById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // 3. SECURITY CHECK: Ensure user owns the account
        if (!targetUser.getEmail().equals(currentPrincipalEmail)) {
            throw new ForbiddenAccessException("Access Denied: You can only delete your own account.");
        }

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
