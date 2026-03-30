package com.example.user.http.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenResponse(
        @JsonProperty("access_token") String AccessToken
) {
}
