package com.beacon.authservice.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal representation of an authenticated user returned to clients alongside
 * the issued token.
 */
@Schema(name = "UserProfile", description = "Metadata about the authenticated user associated with a token.")
public record UserProfile(
        @Schema(description = "Stable subject identifier inside Beacon tokens", example = "google:1234567890") String subject,
        @Schema(description = "User e-mail address", example = "user@example.com") String email,
        @Schema(description = "Display-friendly name", example = "Alex Smith") String displayName,
        @Schema(description = "Avatar URL if available", example = "https://example.com/avatar.png") String avatarUrl) {
}
