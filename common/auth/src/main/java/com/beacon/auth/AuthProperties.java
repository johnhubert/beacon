package com.beacon.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

/**
 * Configuration properties that tune Beacon authentication behaviour.
 * <p>
 * The properties are loaded from the {@code beacon.auth} namespace and are
 * shared across backend services that participate in the authentication model.
 */
@ConfigurationProperties(prefix = "beacon.auth")
@Validated
public class AuthProperties {

    private final boolean devMode;
    private final String jwtSecret;
    private final Duration accessTokenTtl;
    private final Duration refreshInterval;
    private final List<String> audiences;
    private final String issuer;
    private final Google google;
    private final boolean apiEnabled;

    @ConstructorBinding
    public AuthProperties(
            @DefaultValue("false") boolean devMode,
            String jwtSecret,
            @DefaultValue("PT12H") Duration accessTokenTtl,
            @DefaultValue("PT24H") Duration refreshInterval,
            List<String> audiences,
            @DefaultValue("beacon-auth-service") String issuer,
            Google google,
            @DefaultValue("false") boolean apiEnabled) {
        this.devMode = devMode;
        this.jwtSecret = jwtSecret;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshInterval = refreshInterval;
        if (audiences == null || audiences.isEmpty()) {
            this.audiences = new ArrayList<>();
            this.audiences.add("beacon-api");
        } else {
            this.audiences = new ArrayList<>(audiences);
        }
        this.issuer = issuer;
        this.google = google == null ? Google.defaults() : google;
        this.apiEnabled = apiEnabled;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public List<String> getAudiences() {
        return new ArrayList<>(audiences);
    }

    public String getIssuer() {
        return issuer;
    }

    public Google getGoogle() {
        return google;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    /**
     * Properties that control Google OpenID Connect verification.
     */
    public static class Google {
        private final boolean enabled;
        private final String webClientId;
        private final String androidClientId;
        private final String iosClientId;
        private final String clientSecret;
        private final String issuer;

        @ConstructorBinding
        public Google(
                @DefaultValue("true") boolean enabled,
                String webClientId,
                String androidClientId,
                String iosClientId,
                String clientSecret,
                @DefaultValue("https://accounts.google.com") String issuer) {
            this.enabled = enabled;
            this.webClientId = normalize(webClientId);
            this.androidClientId = normalize(androidClientId);
            this.iosClientId = normalize(iosClientId);
            this.clientSecret = normalize(clientSecret);
            this.issuer = issuer;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getWebClientId() {
            return webClientId;
        }

        public String getAndroidClientId() {
            return androidClientId;
        }

        public String getIosClientId() {
            return iosClientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public String getIssuer() {
            return issuer;
        }

        private static String normalize(String value) {
            return StringUtils.hasText(value) ? value : null;
        }

        public static Google defaults() {
            return new Google(true, null, null, null, null, "https://accounts.google.com");
        }
    }
}
