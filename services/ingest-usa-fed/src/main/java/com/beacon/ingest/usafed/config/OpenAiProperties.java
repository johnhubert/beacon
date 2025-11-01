package com.beacon.ingest.usafed.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "beacon.llm.openai")
public record OpenAiProperties(
        URI baseUrl,
        String apiKey,
        String organization,
        Duration requestTimeout)
{

    public OpenAiProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.openai.com/v1") : baseUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        organization = organization == null ? "" : organization.trim();
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }
}
