package com.intellidocAI.backend.config;

import com.intellidocAI.backend.model.User;
import com.intellidocAI.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Find the user in MongoDB by Email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));

        // 2. Map your roles (List<String>) to Spring Security Authorities
        List<SimpleGrantedAuthority> authorities = (user.getRole() == null)
                ? List.of()
                : user.getRole().stream()
                .map(role -> new SimpleGrantedAuthority(role))
                .collect(Collectors.toList());

        // 3. Return the Spring Security User object
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),      // Use Email as username
                user.getPassword(),   // Use the hashed password
                authorities           // User roles
        );
    }
}