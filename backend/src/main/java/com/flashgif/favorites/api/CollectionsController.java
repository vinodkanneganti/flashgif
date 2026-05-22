package com.flashgif.favorites.api;

import com.flashgif.favorites.api.dto.*;
import com.flashgif.favorites.domain.CollectionsService;
import com.flashgif.users.domain.UserRepository;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequiredArgsConstructor
@Tag(name = "Collections", description = "Owned, named folders of media.")
class CollectionsController {

    private final CollectionsService collectionsService;
    private final UserRepository userRepository;

    // ---------------- create / list / read ----------------

    @PostMapping("/api/v1/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Create a new collection owned by the current user.")
    public CollectionResponse create(@Valid @RequestBody CollectionCreateRequest req) {
        var c = collectionsService.create(currentUserId(),
                req.name(), req.description(),
                req.isPublic() != null && req.isPublic());
        return CollectionResponse.from(c);
    }

    @GetMapping("/api/v1/users/me/collections")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "List all of the current user's collections (public + private).")
    public List<CollectionResponse> listOwn() {
        return collectionsService.listOwn(currentUserId()).stream().map(CollectionResponse::from).toList();
    }

    @GetMapping("/api/v1/users/{username}/collections")
    @Operation(summary = "List a user's public collections by username. No auth required.")
    public List<CollectionResponse> listPublicFor(@PathVariable String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        return collectionsService.listPublicFor(user.getId()).stream().map(CollectionResponse::from).toList();
    }

    @GetMapping("/api/v1/collections/{id}")
    @Operation(summary = "Fetch a single collection. Owner can read private; everyone can read public.")
    public CollectionResponse get(@PathVariable UUID id) {
        return CollectionResponse.from(
                collectionsService.requireVisible(id, currentUserIdOrNull()));
    }

    // ---------------- update / delete ----------------

    @PatchMapping("/api/v1/collections/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Owner-only: rename, edit description, toggle visibility.")
    public CollectionResponse update(@PathVariable UUID id, @Valid @RequestBody CollectionUpdateRequest req) {
        return CollectionResponse.from(
                collectionsService.update(id, currentUserId(),
                        req.name(), req.description(), req.isPublic()));
    }

    @DeleteMapping("/api/v1/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Owner-only: delete collection (items cascade).")
    public void delete(@PathVariable UUID id) {
        collectionsService.delete(id, currentUserId());
    }

    // ---------------- items ----------------

    @PostMapping("/api/v1/collections/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Owner-only: add a media to the collection. Idempotent.")
    public void addItem(@PathVariable UUID id, @Valid @RequestBody CollectionItemRequest req) {
        collectionsService.addItem(id, currentUserId(), req.mediaId());
    }

    @DeleteMapping("/api/v1/collections/{id}/items/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Owner-only: remove a media from the collection.")
    public void removeItem(@PathVariable UUID id, @PathVariable UUID mediaId) {
        collectionsService.removeItem(id, currentUserId(), mediaId);
    }

    @GetMapping("/api/v1/collections/{id}/items")
    @Operation(summary = "Paged items in a collection. Owner can read private; everyone can read public.")
    public PagedResponse<CollectionItemResponse> items(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageReq = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var src = collectionsService.listItems(id, currentUserIdOrNull(), pageReq);
        return PagedResponse.from(src, CollectionItemResponse::from);
    }

    // ---------------- helpers ----------------

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    /** Null when unauthenticated — used for public-or-owner GET endpoints. */
    private static UUID currentUserIdOrNull() {
        return UserPrincipal.currentUserId().orElse(null);
    }
}
