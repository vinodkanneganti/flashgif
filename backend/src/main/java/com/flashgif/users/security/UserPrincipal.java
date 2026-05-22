package com.flashgif.users.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authenticated principal built from a parsed JWT. We do not hit the DB on
 * each request — the token's signed claims are authoritative for the lifetime
 * of the access token.
 */
public class UserPrincipal extends AbstractAuthenticationToken {

    private final UUID userId;
    private final String email;

    public UserPrincipal(UUID userId, String email) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
        this.email  = email;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal()   { return userId; }

    public UUID userId() { return userId; }
    public String email() { return email; }

    /** Convenience: returns the current authenticated user id, if any. */
    public static Optional<UUID> currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserPrincipal p) return Optional.of(p.userId());
        return Optional.empty();
    }
}
