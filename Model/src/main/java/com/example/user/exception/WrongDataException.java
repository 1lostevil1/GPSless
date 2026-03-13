package com.example.user.exception;

public record WrongDataException(
        int status,
        String message
) {
}
