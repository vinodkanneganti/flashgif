package com.flashgif.favorites.api.dto;

import com.flashgif.favorites.domain.CollectionItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectionItemResponse(UUID mediaId, OffsetDateTime addedAt) {
    public static CollectionItemResponse from(CollectionItem c) {
        return new CollectionItemResponse(c.getMediaId(), c.getAddedAt());
    }
}
