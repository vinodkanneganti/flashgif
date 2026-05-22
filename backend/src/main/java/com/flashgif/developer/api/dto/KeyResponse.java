package com.flashgif.developer.api.dto;

import com.flashgif.developer.domain.DeveloperKey;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KeyResponse(
        UUID id,
        String name,
        String prefix,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt
) {
    public static KeyResponse from(DeveloperKey k) {
        return new KeyResponse(k.getId(), k.getName(), k.getPrefix(), k.getStatus(),
                k.getCreatedAt(), k.getLastUsedAt(), k.getRevokedAt());
    }
}
