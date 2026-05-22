package com.flashgif.users.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued credential pair. Access token is a JWT; refresh token is an opaque random string.")
public record AuthResponse(
        String accessToken,
        long   expiresInSeconds,
        String refreshToken,
        String tokenType
) {
    public static AuthResponse of(String access, long ttl, String refresh) {
        return new AuthResponse(access, ttl, refresh, "Bearer");
    }
}
