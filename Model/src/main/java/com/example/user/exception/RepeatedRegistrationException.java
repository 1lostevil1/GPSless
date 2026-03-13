package com.example.user.exception;

public record RepeatedRegistrationException(
        int status,
        String message
) {
}
