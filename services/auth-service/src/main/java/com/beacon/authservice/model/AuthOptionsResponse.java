package com.beacon.authservice.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Describes the authentication mechanisms that the client can offer.
 */
@Schema(name = "AuthOptionsResponse", description = "Configuration flags that drive the client-side auth experience.")
public record AuthOptionsResponse(
        @Schema(description = "True when Google authentication is enabled server side") boolean googleEnabled,
        @Schema(description = "Google Web client identifier", example = "12345-web.apps.googleusercontent.com") String googleWebClientId,
        @Schema(description = "Google Android client identifier", example = "12345-android.apps.googleusercontent.com") String googleAndroidClientId,
        @Schema(description = "Google iOS client identifier", example = "12345-ios.apps.googleusercontent.com") String googleIosClientId,
        @Schema(description = "True when demo credentials are accepted") boolean demoEnabled) {
}
