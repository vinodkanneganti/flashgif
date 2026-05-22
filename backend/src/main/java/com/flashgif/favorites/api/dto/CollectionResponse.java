package com.flashgif.favorites.api.dto;

import com.flashgif.favorites.domain.MediaCollection;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectionResponse(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        boolean isPublic,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CollectionResponse from(MediaCollection c) {
        return new CollectionResponse(c.getId(), c.getOwnerId(), c.getName(),
                c.getDescription(), c.isPublic(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
