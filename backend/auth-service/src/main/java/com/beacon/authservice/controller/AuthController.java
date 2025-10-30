package com.beacon.authservice.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.beacon.auth.AuthProperties;
import com.beacon.auth.JwtService;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Authentication entry point for the mobile application. Supports demo
 * username/password flow as well as Google issued ID tokens.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Token issuance endpoints")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    private static final String DEV_SESSION_COOKIE = "BEACON_DEV_SESSION";

    private final DemoAuthenticationService demoAuthenticationService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final JwtDecoder jwtDecoder;

    public AuthController(
            DemoAuthenticationService demoAuthenticationService,
            GoogleTokenVerifier googleTokenVerifier,
            JwtService jwtService,
            AuthProperties authProperties,
            JwtDecoder jwtDecoder) {
        this.demoAuthenticationService = demoAuthenticationService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/options")
    @Operation(summary = "Describe available authentication options",
            description = "Returns feature flags and client identifiers so the UI can enable appropriate login flows.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Options returned",
                            content = @Content(schema = @Schema(implementation = AuthOptionsResponse.class)))
            })
    public AuthOptionsResponse authOptions() {
        boolean demoEnabled = demoAuthenticationService.isDevModeEnabled();
        return new AuthOptionsResponse(false, null, null, null, demoEnabled);
    }

    @PostMapping("/demo")
    @Operation(summary = "Authenticate with demo credentials",
            description = "Generates a JWT when demo credentials are enabled.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or dev mode disabled", content = @Content)
            })
    public AuthResponse demoLogin(@Valid @RequestBody DemoLoginRequest request, HttpServletResponse response) {
        Optional<UserProfile> profile = demoAuthenticationService.authenticate(request.username(), request.password());
        if (profile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid demo credentials or dev mode disabled");
        }
        AuthResponse authResponse = issueToken(profile.get());
        writeDevSessionCookie(response, authResponse);
        return authResponse;
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google ID token",
            description = "Validates a Google OpenID Connect ID token and returns a Beacon JWT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Google token invalid", content = @Content)
            })
    public AuthResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        Optional<GoogleIdToken.Payload> payload = googleTokenVerifier.verify(request.idToken());
        GoogleIdToken.Payload validPayload = payload.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        UserProfile profile = new UserProfile(
                validPayload.getSubject(),
                validPayload.getEmail(),
                (String) validPayload.get("name"),
                (String) validPayload.get("picture"));
        AuthResponse authResponse = issueToken(profile);
        writeDevSessionCookie(response, authResponse);
        return authResponse;
    }

    @GetMapping("/session")
    @Operation(summary = "Return the active development session when present",
            description = "Reads the Beacon development session cookie and returns the associated profile.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Active session",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No active session", content = @Content)
            })
    public AuthResponse session(@CookieValue(name = DEV_SESSION_COOKIE, required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active session");
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Instant expiresAt = jwt.getExpiresAt();
            UserProfile profile = new UserProfile(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("avatarUrl"));
            return new AuthResponse(token, expiresAt, profile);
        } catch (JwtException ex) {
            LOGGER.warn("Failed to decode dev session cookie", ex);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear the active development session cookie",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Session cleared")
            })
    public void logout(HttpServletResponse response) {
        clearDevSessionCookie(response);
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

    private void writeDevSessionCookie(HttpServletResponse response, AuthResponse authResponse) {
        if (!demoAuthenticationService.isDevModeEnabled()) {
            return;
        }
        Duration ttl = authProperties.getAccessTokenTtl();
        ResponseCookie cookie = ResponseCookie.from(DEV_SESSION_COOKIE, authResponse.accessToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearDevSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(DEV_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
