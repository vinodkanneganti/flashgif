package com.flashgif.media.transcode;

import java.util.UUID;

public record TranscodeMessage(
        UUID uploadId,
        String s3Key,
        String originalContentType,
        long originalSize
) {}
