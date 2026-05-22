package com.flashgif.users.api;

import com.flashgif.users.api.dto.MeResponse;
import com.flashgif.users.api.dto.UpdateMeRequest;
import com.flashgif.users.domain.User;
import com.flashgif.users.domain.UserService;
import com.flashgif.users.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile.")
@SecurityRequirement(name = "bearer-jwt")
class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Return the current authenticated user's profile.")
    public MeResponse me() {
        return toResponse(userService.require(currentUserId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's display name.")
    public MeResponse updateMe(@Valid @RequestBody UpdateMeRequest req) {
        return toResponse(userService.updateDisplayName(currentUserId(), req.displayName()));
    }

    private static UUID currentUserId() {
        return UserPrincipal.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private static MeResponse toResponse(User u) {
        return new MeResponse(u.getId(), u.getEmail(), u.getUsername(),
                u.getDisplayName(), u.getStatus(), u.getCreatedAt());
    }
}
