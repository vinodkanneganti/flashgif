package com.flashgif.developer.security;

import com.flashgif.developer.domain.DeveloperKey;
import com.flashgif.developer.domain.DeveloperKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates third-party requests to the developer chain. Soft-fail: invalid
 * tokens clear the SecurityContext and let the chain proceed to authorize, which
 * 401s via the dev chain's entry point. Same shape as {@code UserJwtFilter}.
 */
@Component
@RequiredArgsConstructor
public class DeveloperApiKeyFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final DeveloperKeyService keyService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String raw = header.substring(BEARER.length()).trim();
            Optional<DeveloperKey> resolved = keyService.resolveActive(raw);
            if (resolved.isPresent()) {
                DeveloperKey key = resolved.get();
                SecurityContextHolder.getContext().setAuthentication(
                        new DeveloperPrincipal(key.getId(), key.getOwnerId()));
            } else {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
