package com.flashgif.search.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Autocomplete suggestion.")
public record Suggestion(
        String text,
        @Schema(description = "Where this suggestion came from: 'title' or 'tag'.") String source
) {}
