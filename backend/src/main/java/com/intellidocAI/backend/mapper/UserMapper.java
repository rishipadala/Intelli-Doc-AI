package com.intellidocAI.backend.mapper;

import com.intellidocAI.backend.dto.UserDTO;
import com.intellidocAI.backend.model.User;
import org.springframework.stereotype.Component;


@Component
public class UserMapper {
    public UserDTO toDto(User user) {
        if (user == null) {
            return null;
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
