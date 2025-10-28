package com.beacon.ingest.usafed.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "beacon.congress")
public record CongressApiProperties(
        URI baseUrl,
        String apiKey,
        Duration pollInterval,
        String chamber,
        int congressNumber)
{
}
