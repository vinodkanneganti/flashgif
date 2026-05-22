package com.flashgif.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 access-token issuer/parser. Refresh tokens are *not* JWTs (see
 * AuthService) — they are opaque random strings hashed in the DB.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final JwtProperties props;

    private SecretKey key() {
        // Accept either a raw or base64-encoded secret; minimum 256 bits for HS256.
        byte[] bytes = props.secret().getBytes();
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "flashgif.auth.secret must be at least 32 bytes (256 bits) for HS256");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public IssuedAccessToken issue(UUID userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());
        String jwt = Jwts.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .claim("email", email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
        return new IssuedAccessToken(jwt, exp, props.accessTokenTtl().toSeconds());
    }

    /** Parses + validates. Throws on invalid/expired token. */
    public ParsedAccessToken parse(String jwt) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .requireIssuer(props.issuer())
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(jwt);
            Claims c = jws.getPayload();
            return new ParsedAccessToken(UUID.fromString(c.getSubject()), c.get("email", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid access token: " + ex.getMessage(), ex);
        }
    }

    public record IssuedAccessToken(String jwt, Instant expiresAt, long expiresInSeconds) {}
    public record ParsedAccessToken(UUID userId, String email) {}

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String msg, Throwable cause) { super(msg, cause); }
    }
}
