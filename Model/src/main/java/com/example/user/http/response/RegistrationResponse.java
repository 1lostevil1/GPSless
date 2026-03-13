package com.example.user.http.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationResponse(
        @JsonProperty("username") String username,
        @JsonProperty("email") String email
) {
}
