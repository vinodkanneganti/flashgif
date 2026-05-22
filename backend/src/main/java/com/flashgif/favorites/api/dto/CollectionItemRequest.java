package com.flashgif.favorites.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CollectionItemRequest(@NotNull UUID mediaId) {}
