package com.intellidocAI.backend.service;

import com.intellidocAI.backend.dto.UserDTO;
import com.intellidocAI.backend.mapper.UserMapper;
import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    // Create or Update User
    public User saveUser(User user) {
        // Hash the password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    // Get All Users
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    // Get Users By ID
    // Modify the service method to return the DTO directly
    public Optional<UserDTO> getUserById(String id) {
        return userRepository.findById(id)
                .map(userMapper::toDto);
    }


    // Get User By Email
    public Optional<UserDTO> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDto);
    }

    /**
     * A helper method to find a raw User entity by its ID.
     * This is used internally for the update process in the controller.
     */
    public Optional<User> findUserEntityById(String id) {
        return userRepository.findById(id);
    }

    // Delete User
    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

}


