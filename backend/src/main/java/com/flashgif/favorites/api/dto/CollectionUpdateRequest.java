package com.flashgif.favorites.api.dto;

import jakarta.validation.constraints.Size;

public record CollectionUpdateRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 4000)         String description,
        Boolean                   isPublic
) {}
