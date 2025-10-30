package com.beacon.authservice.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload containing a Google OpenID Connect ID token that the auth
 * service will validate.
 */
@Schema(name = "GoogleLoginRequest", description = "Payload used to exchange a Google ID token for a Beacon JWT.")
public record GoogleLoginRequest(
        @NotBlank(message = "idToken is required")
        @Schema(description = "Google OpenID Connect ID token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...")
        String idToken) {
}
