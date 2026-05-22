package com.flashgif.developer.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class DeveloperKeyService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RAW_KEY_BYTES = 32;     // 256 bits → 43-char base64url
    private static final String KEY_PREFIX = "fg_";  // matches secret-scanner conventions

    private final DeveloperKeyRepository repository;

    /** Mints a new key and returns the **raw token exactly once**. Only the hash is persisted. */
    @Transactional
    public IssuedKey issue(UUID ownerId, String name) {
        String raw = KEY_PREFIX + randomToken();
        byte[] hash = sha256(raw);

        DeveloperKey key = new DeveloperKey();
        key.setOwnerId(ownerId);
        key.setName(name);
        key.setKeyHash(hash);
        key.setPrefix(raw.substring(0, Math.min(16, raw.length())));   // "fg_ABCDEFGH..."
        DeveloperKey saved = repository.save(key);

        return new IssuedKey(saved.getId(), saved.getName(), saved.getPrefix(),
                saved.getStatus(), saved.getCreatedAt(), raw);
    }

    @Transactional(readOnly = true)
    public List<DeveloperKey> list(UUID ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public void revoke(UUID ownerId, UUID keyId) {
        DeveloperKey key = repository.findById(keyId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Key not found"));
        if (!key.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(NOT_FOUND, "Key not found");   // don't leak existence
        }
        key.setStatus(DeveloperKeyStatus.REVOKED.dbValue());
        key.setRevokedAt(OffsetDateTime.now());
        repository.save(key);
    }

    /** Resolves a raw token presented by a third-party client. */
    @Transactional(readOnly = true)
    public Optional<DeveloperKey> resolveActive(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        return repository.findByKeyHash(sha256(rawKey)).filter(DeveloperKey::isActive);
    }

    // ---------------- helpers ----------------

    static String randomToken() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IssuedKey(UUID id, String name, String prefix, String status,
                            OffsetDateTime createdAt, String rawKey) {}
}
