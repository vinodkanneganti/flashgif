package com.flashgif.search.api.dto;

import com.flashgif.media.api.dto.MediaSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paged search result.")
public record SearchResponse(
        List<MediaSummary> items,
        int page,
        int size,
        long total,
        @Schema(name = "took_ms") long tookMs
) {}
