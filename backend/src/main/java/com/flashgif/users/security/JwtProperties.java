package com.flashgif.users.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("flashgif.auth")
public record JwtProperties(
        /** Base64-encoded 256-bit secret. Set via env var in real envs. */
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank())        issuer = "flashgif";
        if (accessTokenTtl == null)                    accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null)                   refreshTokenTtl = Duration.ofDays(30);
    }
}
