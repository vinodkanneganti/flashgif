package com.flashgif.channels.api.dto;

import com.flashgif.media.api.dto.MediaSummary;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ChannelResponse(
        String username,
        String displayName,
        String bio,
        String websiteUrl,
        String avatarUrl,
        String bannerUrl,
        Map<String, String> socialLinks,
        boolean isVerified,
        long uploadCount,
        List<MediaSummary> topMedia,
        OffsetDateTime createdAt
) {}
