package com.flashgif.favorites.api;

import com.flashgif.favorites.api.dto.FavoriteRequest;
import com.flashgif.favorites.api.dto.FavoriteResponse;
import com.flashgif.favorites.api.dto.PagedResponse;
import com.flashgif.favorites.domain.FavoritesService;
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
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Per-user flat favorites list.")
@SecurityRequirement(name = "bearer-jwt")
class FavoritesController {

    private final FavoritesService favoritesService;

    @PostMapping("/api/v1/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Favorite a media. Idempotent.")
    public void favorite(@Valid @RequestBody FavoriteRequest req) {
        favoritesService.favorite(currentUserId(), req.mediaId());
    }

    @DeleteMapping("/api/v1/favorites/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a favorite. Idempotent.")
    public void unfavorite(@PathVariable UUID mediaId) {
        favoritesService.unfavorite(currentUserId(), mediaId);
    }

    @GetMapping("/api/v1/users/me/favorites")
    @Operation(summary = "List the current user's favorites, newest first.")
    public PagedResponse<FavoriteResponse> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageReq = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var src = favoritesService.list(currentUserId(), pageReq);
        return PagedResponse.from(src, f -> new FavoriteResponse(f.getMediaId(), f.getCreatedAt()));
    }

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
