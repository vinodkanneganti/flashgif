package com.flashgif.favorites.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionCreateRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @Size(max = 4000)                   String description,
        Boolean                             isPublic
) {}
