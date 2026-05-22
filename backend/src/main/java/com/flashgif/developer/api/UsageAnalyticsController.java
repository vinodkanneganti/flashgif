package com.flashgif.developer.api;

import com.flashgif.developer.api.dto.UsageAnalyticsResponse;
import com.flashgif.developer.domain.DeveloperKey;
import com.flashgif.developer.domain.DeveloperKeyService;
import com.flashgif.developer.domain.UsageRecorder;
import com.flashgif.users.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usage/analytics")
@RequiredArgsConstructor
@Tag(name = "Developer Usage", description = "Per-key and aggregate usage analytics.")
@SecurityRequirement(name = "bearer-jwt")
class UsageAnalyticsController {

    private static final int MAX_WINDOW_DAYS = 35;     // matches Redis TTL on counters
    private static final int DEFAULT_WINDOW  = 30;

    private final DeveloperKeyService keyService;
    private final UsageRecorder usageRecorder;

    @GetMapping
    @Operation(summary = "Daily request counts for one key or all of the user's keys.")
    public UsageAnalyticsResponse get(
            @RequestParam(required = false) UUID keyId,
            @RequestParam(defaultValue = "30") int days
    ) {
        int windowDays = Math.max(1, Math.min(days, MAX_WINDOW_DAYS));
        UUID userId = currentUserId();

        List<DeveloperKey> targetKeys = resolveTargetKeys(userId, keyId);

        LocalDate today = LocalDate.now();
        List<UsageAnalyticsResponse.DayCount> byDay = new ArrayList<>(windowDays);
        long total = 0;

        for (int i = windowDays - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long dayCount = 0;
            for (DeveloperKey k : targetKeys) {
                dayCount += usageRecorder.countForDay(k.getId(), day);
            }
            byDay.add(new UsageAnalyticsResponse.DayCount(day, dayCount));
            total += dayCount;
        }

        return new UsageAnalyticsResponse(keyId, windowDays, total, byDay);
    }

    private List<DeveloperKey> resolveTargetKeys(UUID userId, UUID keyId) {
        List<DeveloperKey> owned = keyService.list(userId);
        if (keyId == null) return owned;
        return owned.stream()
                .filter(k -> k.getId().equals(keyId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Key not found"));
    }

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
