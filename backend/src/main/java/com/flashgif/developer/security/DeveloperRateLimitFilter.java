package com.flashgif.developer.security;

import com.flashgif.developer.domain.UsageRecorder;
import com.flashgif.developer.ratelimit.TokenBucketLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs after {@link DeveloperApiKeyFilter}. If a developer principal is present,
 * consults the token bucket; rejects with 429 + {@code Retry-After} when empty.
 * Records usage on the allowed path only — 429s don't count.
 *
 * <p>If no developer principal is present (unauthenticated dev call) we do
 * nothing — the authorization layer will 401 the request itself.
 */
@Component
@RequiredArgsConstructor
public class DeveloperRateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketLimiter limiter;
    private final UsageRecorder usageRecorder;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<DeveloperPrincipal> principal = DeveloperPrincipal.current();
        if (principal.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }

        TokenBucketLimiter.TryAcquireResult outcome = limiter.tryAcquire(principal.get().keyId());
        if (!outcome.allowed()) {
            res.setHeader("Retry-After", String.valueOf(outcome.retryAfterSeconds()));
            res.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        usageRecorder.record(principal.get().keyId());
        chain.doFilter(req, res);
    }
}
