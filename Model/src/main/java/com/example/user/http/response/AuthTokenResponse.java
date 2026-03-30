package com.example.user.http.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthTokenResponse(
        @JsonProperty("access_token") String AccessToken,
        @JsonProperty("refresh_token") String refreshToken
) {
}
