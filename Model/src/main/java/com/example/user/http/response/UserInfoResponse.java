package com.example.user.http.response;

import java.util.List;

public record UserInfoResponse(
        String username,
        List<String> roles
) {}
