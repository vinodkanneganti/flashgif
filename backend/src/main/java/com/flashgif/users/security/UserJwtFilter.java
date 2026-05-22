package com.flashgif.users.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UserJwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                JwtService.ParsedAccessToken parsed = jwtService.parse(token);
                UserPrincipal principal = new UserPrincipal(parsed.userId(), parsed.email());
                SecurityContextHolder.getContext().setAuthentication(principal);
            } catch (JwtService.InvalidTokenException ex) {
                // Leave context unauthenticated; downstream authorization will 401/403.
                // Don't short-circuit here — permitAll endpoints should still flow through.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
