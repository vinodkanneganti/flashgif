package com.flashgif.developer.api.dto;

import com.flashgif.developer.domain.DeveloperKeyService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Issued key. The raw key is returned exactly ONCE — store it now; we only keep a hash.")
public record IssuedKeyResponse(
        UUID id,
        String name,
        String prefix,
        String status,
        OffsetDateTime createdAt,
        @Schema(description = "The raw API key. Cannot be retrieved again.") String key
) {
    public static IssuedKeyResponse from(DeveloperKeyService.IssuedKey k) {
        return new IssuedKeyResponse(k.id(), k.name(), k.prefix(), k.status(), k.createdAt(), k.rawKey());
    }
}
