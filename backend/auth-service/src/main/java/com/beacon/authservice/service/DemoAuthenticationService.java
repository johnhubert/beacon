package com.beacon.authservice.service;

import java.util.Optional;

import com.beacon.auth.AuthProperties;
import com.beacon.authservice.model.UserProfile;
import org.springframework.stereotype.Component;

/**
 * Handles the demo credential flow used during development.
 */
@Component
public class DemoAuthenticationService {

    private final AuthProperties properties;

    public DemoAuthenticationService(AuthProperties properties) {
        this.properties = properties;
    }

    public boolean isDevModeEnabled() {
        return properties.isDevMode();
    }

    public Optional<UserProfile> authenticate(String username, String password) {
        if (!properties.isDevMode()) {
            return Optional.empty();
        }
        if ("demo".equals(username) && "demo".equals(password)) {
            return Optional.of(new UserProfile("demo-user", "demo@beacon.local", "Demo User", null));
        }
        return Optional.empty();
    }
}
