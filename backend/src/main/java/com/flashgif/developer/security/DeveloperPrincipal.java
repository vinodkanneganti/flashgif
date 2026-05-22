package com.flashgif.developer.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authenticated principal for third-party developer requests. Carries the key id
 * (for rate limiting + usage attribution) and owner id (for analytics scoping).
 */
public class DeveloperPrincipal extends AbstractAuthenticationToken {

    private final UUID keyId;
    private final UUID ownerId;

    public DeveloperPrincipal(UUID keyId, UUID ownerId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        this.keyId = keyId;
        this.ownerId = ownerId;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal()   { return keyId; }

    public UUID keyId()   { return keyId; }
    public UUID ownerId() { return ownerId; }

    public static Optional<DeveloperPrincipal> current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof DeveloperPrincipal p) return Optional.of(p);
        return Optional.empty();
    }
}
