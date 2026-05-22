package com.flashgif.favorites.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FavoriteResponse(UUID mediaId, OffsetDateTime createdAt) {}
