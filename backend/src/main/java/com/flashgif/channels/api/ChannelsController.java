package com.flashgif.channels.api;

import com.flashgif.channels.api.dto.ChannelResponse;
import com.flashgif.channels.api.dto.UpdateProfileRequest;
import com.flashgif.channels.domain.ChannelsService;
import com.flashgif.favorites.api.dto.PagedResponse;
import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.users.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Public creator profiles + upload history.")
class ChannelsController {

    private final ChannelsService channelsService;

    @GetMapping("/{username}")
    @Operation(summary = "Public channel: profile, upload count, top-5 most-favorited media.")
    public ChannelResponse get(@PathVariable String username) {
        return channelsService.getByUsername(username);
    }

    @GetMapping("/{username}/media")
    @Operation(summary = "Paged published-only upload history for the given channel.")
    public PagedResponse<MediaSummary> media(
            @PathVariable String username,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageReq = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var src = channelsService.listMedia(username, pageReq);
        return PagedResponse.from(src, m -> m);
    }

    @PatchMapping("/profile")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Update the current user's profile fields.")
    public ChannelResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return channelsService.updateProfile(currentUserId(), req);
    }

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
