package com.example.user.dto;

import com.example.user.entity.Role;

public record UserDTO(
        String username,
        String email,
        String password,
        Role role
)  {
}
