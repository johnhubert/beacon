package com.beacon.authservice.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request submitted for the demo credential login flow.
 */
@Schema(name = "DemoLoginRequest", description = "Payload used to authenticate with demo credentials.")
public record DemoLoginRequest(
        @NotBlank(message = "username is required")
        @Schema(description = "Demo account username", example = "demo") String username,
        @NotBlank(message = "password is required")
        @Schema(description = "Demo account password", example = "demo") String password) {
}
