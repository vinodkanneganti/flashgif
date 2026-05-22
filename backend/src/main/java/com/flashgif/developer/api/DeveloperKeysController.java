package com.flashgif.developer.api;

import com.flashgif.developer.api.dto.CreateKeyRequest;
import com.flashgif.developer.api.dto.IssuedKeyResponse;
import com.flashgif.developer.api.dto.KeyResponse;
import com.flashgif.developer.domain.DeveloperKeyService;
import com.flashgif.users.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/developer/keys")
@RequiredArgsConstructor
@Tag(name = "Developer Keys", description = "Issue and manage API keys for third-party integrations.")
@SecurityRequirement(name = "bearer-jwt")
class DeveloperKeysController {

    private final DeveloperKeyService keyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a new API key. Raw key returned only once.")
    public IssuedKeyResponse create(@Valid @RequestBody CreateKeyRequest req) {
        return IssuedKeyResponse.from(keyService.issue(currentUserId(), req.name()));
    }

    @GetMapping
    @Operation(summary = "List the current user's API keys (metadata only).")
    public List<KeyResponse> list() {
        return keyService.list(currentUserId()).stream().map(KeyResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an API key. Subsequent calls with that key return 401.")
    public void revoke(@PathVariable UUID id) {
        keyService.revoke(currentUserId(), id);
    }

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
