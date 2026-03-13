package com.example.user.http.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

public record RegistrationRequest(
        @NotNull
        @JsonProperty("username") String username,

        @NotNull
        @JsonProperty("email") String email,

        @NotNull
        @JsonProperty("password") String password
) {
}
