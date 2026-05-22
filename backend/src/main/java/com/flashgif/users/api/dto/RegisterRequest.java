package com.flashgif.users.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$",
                message = "username must be 3-30 chars, letters/digits/underscore only")
        String username,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotBlank @Size(min = 1, max = 50)   String displayName
) {}
