package com.flashgif.media.api.dto;

import jakarta.validation.constraints.*;

public record UploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64)  String contentType,
        @Positive                    long size
) {}
