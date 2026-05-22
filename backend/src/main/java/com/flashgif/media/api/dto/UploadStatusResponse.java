package com.flashgif.media.api.dto;

import com.flashgif.media.domain.UploadStatus;

import java.util.Map;
import java.util.UUID;

public record UploadStatusResponse(
        UUID uploadId,
        UploadStatus status,
        String errorMessage,
        Integer width,
        Integer height,
        Integer durationMs,
        Map<String, String> renditionUrls,
        UUID mediaId
) {}
