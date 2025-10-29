package com.beacon.authservice.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.beacon.auth.JwtService;
import com.beacon.auth.AuthProperties;
import com.beacon.authservice.model.AuthOptionsResponse;
import com.beacon.authservice.model.AuthResponse;
import com.beacon.authservice.model.UserProfile;
import com.beacon.authservice.model.request.DemoLoginRequest;
import com.beacon.authservice.model.request.GoogleLoginRequest;
import com.beacon.authservice.service.DemoAuthenticationService;
import com.beacon.authservice.service.GoogleTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication entry point for the mobile application. Supports demo
 * username/password flow as well as Google issued ID tokens.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Token issuance endpoints")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final DemoAuthenticationService demoAuthenticationService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    public AuthController(
            DemoAuthenticationService demoAuthenticationService,
            GoogleTokenVerifier googleTokenVerifier,
            JwtService jwtService,
            AuthProperties authProperties) {
        this.demoAuthenticationService = demoAuthenticationService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
    }

    @GetMapping("/options")
    @Operation(summary = "Describe available authentication options",
            description = "Returns feature flags and client identifiers so the UI can enable appropriate login flows.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Options returned",
                            content = @Content(schema = @Schema(implementation = AuthOptionsResponse.class)))
            })
    public AuthOptionsResponse authOptions() {
        AuthProperties.Google google = authProperties.getGoogle();
        boolean googleEnabled = false;
        String webClientId = null;
        String androidClientId = null;
        String iosClientId = null;
        if (google != null && google.isEnabled()) {
            if (google.getWebClientId() != null && !google.getWebClientId().isBlank()) {
                googleEnabled = true;
                webClientId = google.getWebClientId();
            }
            if (google.getAndroidClientId() != null && !google.getAndroidClientId().isBlank()) {
                googleEnabled = true;
                androidClientId = google.getAndroidClientId();
            }
            if (google.getIosClientId() != null && !google.getIosClientId().isBlank()) {
                googleEnabled = true;
                iosClientId = google.getIosClientId();
            }
        }
        boolean demoEnabled = demoAuthenticationService.isDevModeEnabled();
        return new AuthOptionsResponse(googleEnabled, webClientId, androidClientId, iosClientId, demoEnabled);
    }

    @PostMapping("/demo")
    @Operation(summary = "Authenticate with demo credentials",
            description = "Generates a JWT when demo credentials are enabled.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or dev mode disabled", content = @Content)
            })
    public AuthResponse demoLogin(@Valid @RequestBody DemoLoginRequest request) {
        Optional<UserProfile> profile = demoAuthenticationService.authenticate(request.username(), request.password());
        if (profile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid demo credentials or dev mode disabled");
        }
        return issueToken(profile.get());
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google ID token",
            description = "Validates a Google OpenID Connect ID token and returns a Beacon JWT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Google token invalid", content = @Content)
            })
    public AuthResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        Optional<GoogleIdToken.Payload> payload = googleTokenVerifier.verify(request.idToken());
        GoogleIdToken.Payload validPayload = payload.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        UserProfile profile = new UserProfile(
                validPayload.getSubject(),
                validPayload.getEmail(),
                (String) validPayload.get("name"),
                (String) validPayload.get("picture"));
        return issueToken(profile);
    }

    private AuthResponse issueToken(UserProfile profile) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", profile.email());
        claims.put("name", profile.displayName());
        if (profile.avatarUrl() != null) {
            claims.put("avatarUrl", profile.avatarUrl());
        }

        String token = jwtService.generateToken(profile.subject(), claims);
        Instant expiresAt = Instant.now().plus(authProperties.getAccessTokenTtl());
        LOGGER.debug("Issued token for subject {} expiring at {}", profile.subject(), expiresAt);
        return new AuthResponse(token, expiresAt, profile);
    }
}
