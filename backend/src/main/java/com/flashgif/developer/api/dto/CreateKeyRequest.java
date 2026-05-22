package com.flashgif.developer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKeyRequest(
        @NotBlank @Size(min = 1, max = 100) String name
) {}
