package com.flashgif.media.api.dto;

import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

public record MetadataRequest(
        @NotNull UUID uploadId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotBlank @Pattern(regexp = "gif|sticker") String type,
        @Pattern(regexp = "g|pg|pg13|r") String contentRating,
        @Size(max = 20) Set<@NotBlank @Size(max = 64) String> tags
) {}
