package com.flashgif.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Presigned-URL bundle to PUT the original file to S3.")
public record UploadResponse(
        UUID uploadId,
        String s3Key,
        @Schema(description = "Presigned PUT URL. Use HTTP PUT with the file body and the same Content-Type.") String presignedUrl,
        OffsetDateTime expiresAt
) {}
