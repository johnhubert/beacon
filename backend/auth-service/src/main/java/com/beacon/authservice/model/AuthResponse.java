package com.beacon.authservice.model;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a signed-in session returned from the authentication API.
 */
@Schema(name = "AuthResponse", description = "JWT access token payload returned after successful authentication.")
public record AuthResponse(
        @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,
        @Schema(description = "Token expiration timestamp", type = "string", format = "date-time")
        Instant expiresAt,
        @Schema(description = "Authenticated user profile")
        UserProfile profile) {
}
