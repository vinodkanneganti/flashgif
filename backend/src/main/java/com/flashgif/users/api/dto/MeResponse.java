package com.flashgif.users.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        OffsetDateTime createdAt
) {}
