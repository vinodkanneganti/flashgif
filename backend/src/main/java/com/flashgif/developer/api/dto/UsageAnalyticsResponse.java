package com.flashgif.developer.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UsageAnalyticsResponse(
        UUID keyId,                          // null when aggregated across all keys
        int windowDays,
        long totalRequests,
        List<DayCount> byDay
) {
    public record DayCount(LocalDate date, long count) {}
}
