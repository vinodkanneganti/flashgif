package com.flashgif.media.api.dto;

import com.flashgif.media.domain.Media;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compact projection of a {@link Media}. Returned by search, trending,
 * channel uploads, and any other endpoint that lists media items.
 */
@Schema(description = "Compact media projection returned by search/trending/channel endpoints.")
public record MediaSummary(
        String id,
        String title,
        List<String> tags,
        String type,
        @Schema(name = "content_rating") String contentRating,
        @Schema(name = "view_count") long viewCount,
        @Schema(name = "favorite_count") long favoriteCount,
        Integer width,
        Integer height,
        @Schema(name = "rendition_urls") Map<String, String> renditionUrls,
        @Schema(name = "created_at") OffsetDateTime createdAt
) {
    /** Build directly from a JPA {@link Media} row (e.g. channel uploads, top-N lists). */
    public static MediaSummary from(Media m) {
        return new MediaSummary(
                m.getId().toString(),
                m.getTitle(),
                new ArrayList<>(m.getTags()),
                m.getType(),
                m.getContentRating(),
                m.getViewCount(),
                m.getFavoriteCount(),
                m.getWidth(),
                m.getHeight(),
                m.getRenditionUrls(),
                m.getCreatedAt());
    }
}
