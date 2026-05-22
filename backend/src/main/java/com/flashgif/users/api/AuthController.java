package com.flashgif.users.api;

import com.flashgif.users.api.dto.*;
import com.flashgif.users.domain.AuthService;
import com.flashgif.users.domain.User;
import com.flashgif.users.domain.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Email/password registration, login, refresh, logout.")
class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account and return an initial credential pair.")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        User user = userService.register(req.email(), req.username(), req.password(), req.displayName());
        AuthService.IssuedSession s = authService.issueForUser(user, http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(s.accessToken(), s.accessExpiresInSeconds(), s.refreshToken());
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT access token + refresh token.")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        AuthService.IssuedSession s = authService.login(req.email(), req.password(),
                http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(s.accessToken(), s.accessExpiresInSeconds(), s.refreshToken());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a fresh access+refresh pair. Old refresh is revoked.")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        AuthService.IssuedSession s = authService.refresh(req.refreshToken(),
                http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(s.accessToken(), s.accessExpiresInSeconds(), s.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token. Access tokens remain valid until they expire.")
    public void logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
