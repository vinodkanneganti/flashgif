package com.flashgif.users.domain;

import com.flashgif.users.security.JwtProperties;
import com.flashgif.users.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;     // 256 bits

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final JwtProperties jwtProps;

    @Transactional
    public IssuedSession issueForUser(User user, String userAgent, String ip) {
        JwtService.IssuedAccessToken access = jwtService.issue(user.getId(), user.getEmail());
        String raw = generateRawRefresh();
        byte[] hash = sha256(raw);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hash);
        rt.setExpiresAt(OffsetDateTime.now().plus(jwtProps.refreshTokenTtl()));
        rt.setUserAgent(truncate(userAgent, 255));
        rt.setIp(ip);
        refreshTokenRepository.save(rt);

        return new IssuedSession(access.jwt(), access.expiresInSeconds(), raw);
    }

    @Transactional
    public IssuedSession login(String email, String password, String userAgent, String ip) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));
        if (!userService.passwordMatches(user, password)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Account disabled");
        }
        return issueForUser(user, userAgent, ip);
    }

    /** Rotates the refresh token: revokes the presented one and issues a new pair. */
    @Transactional
    public IssuedSession refresh(String rawRefreshToken, String userAgent, String ip) {
        byte[] hash = sha256(rawRefreshToken);
        Optional<RefreshToken> opt = refreshTokenRepository.findByTokenHash(hash);
        if (opt.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");

        RefreshToken existing = opt.get();
        if (!existing.isActive()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh token revoked or expired");
        }

        User user = userService.require(existing.getUserId());
        IssuedSession next = issueForUser(user, userAgent, ip);

        existing.setRevokedAt(OffsetDateTime.now());
        existing.setLastUsedAt(OffsetDateTime.now());
        // replaced_by is set by issueForUser indirectly; we set it explicitly:
        refreshTokenRepository.findByTokenHash(sha256(next.refreshToken()))
                .ifPresent(rt -> existing.setReplacedBy(rt.getId()));
        refreshTokenRepository.save(existing);

        return next;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        byte[] hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(rt);
        });
    }

    private static String generateRawRefresh() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record IssuedSession(String accessToken, long accessExpiresInSeconds, String refreshToken) {}
}
