package com.beacon.authservice.service;

import com.beacon.auth.AuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Verifies Google issued ID tokens before we mint an internal access token.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;
    private final AuthProperties properties;

    public GoogleTokenVerifier(AuthProperties properties) {
        this.properties = properties;
        AuthProperties.Google google = properties.getGoogle();
        java.util.List<String> audiences = new java.util.ArrayList<>();
        if (google != null) {
            if (StringUtils.hasText(google.getWebClientId())) {
                audiences.add(google.getWebClientId());
            }
            if (StringUtils.hasText(google.getAndroidClientId())) {
                audiences.add(google.getAndroidClientId());
            }
            if (StringUtils.hasText(google.getIosClientId())) {
                audiences.add(google.getIosClientId());
            }
        }
        if (google == null || !google.isEnabled() || audiences.isEmpty()) {
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(audiences)
                    .setIssuer(google.getIssuer())
                    .build();
        }
    }

    public Optional<GoogleIdToken.Payload> verify(String idToken) {
        Assert.hasText(idToken, "idToken must not be blank");
        if (verifier == null) {
            LOGGER.warn("Google client id not configured; skipping verification");
            return Optional.empty();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            return token == null ? Optional.empty() : Optional.of(token.getPayload());
        } catch (GeneralSecurityException | IOException ex) {
            LOGGER.error("Failed to verify Google ID token", ex);
            return Optional.empty();
        }
    }
}
