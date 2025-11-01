package com.beacon.ingest.usafed.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "beacon.congress")
public record CongressApiProperties(
        URI baseUrl,
        String apiKey,
        Duration pollInterval,
        String chamber,
        int congressNumber,
        List<Integer> additionalCongresses)
{

    public CongressApiProperties {
        if (additionalCongresses == null) {
            additionalCongresses = List.of();
        } else {
            additionalCongresses = additionalCongresses.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * Returns the ordered set of Congress numbers that should be consulted during roster refresh operations.
     * The currently configured {@code congressNumber} is always included even when it is absent from the
     * supplemental list provided via configuration.
     *
     * @return non-empty ordered list of unique Congress identifiers
     */
    public List<Integer> rosterCongresses() {
        LinkedHashSet<Integer> congresses = new LinkedHashSet<>();
        if (congressNumber > 0) {
            congresses.add(congressNumber);
        }
        additionalCongresses.stream()
                .filter(Objects::nonNull)
                .filter(number -> number > 0)
                .forEach(congresses::add);
        if (congresses.isEmpty()) {
            congresses.add(118); // Provide a sensible default matching the current Congress when configuration is absent.
        }
        return List.copyOf(congresses);
    }
}
