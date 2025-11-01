package com.beacon.congress.client;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable configuration for {@link CongressGovClient} instances.
 */
public record CongressGovClientConfig(String baseUrl, String apiKey, Duration requestTimeout) {

    private static final String DEFAULT_BASE_URL = "https://api.congress.gov/v3";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    public static Builder builder() {
        return new Builder();
    }

    public static CongressGovClientConfig fromEnvironment(Map<String, String> env) {
        String apiKey = Optional.ofNullable(env.get("CONGRESS_API_KEY"))
                .filter(value -> !value.isBlank())
                .orElse(null);
        return builder().apiKey(apiKey).build();
    }

    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiKey;
        private Duration requestTimeout = DEFAULT_TIMEOUT;

        public Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.isBlank()) {
                this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            }
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.isBlank()) {
                this.apiKey = apiKey;
            }
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            if (timeout != null) {
                this.requestTimeout = timeout;
            }
            return this;
        }

        public CongressGovClientConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("API key must be provided via CONGRESS_API_KEY or builder");
            }
            return new CongressGovClientConfig(baseUrl, apiKey, requestTimeout);
        }
    }
}
