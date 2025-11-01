package com.beacon.ingest.usafed.config;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
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

    private static final int FIRST_CONGRESS_YEAR = 1789;
    private static final int FIRST_CONGRESS_NUMBER = 1;
    private static final Month CONGRESS_START_MONTH = Month.JANUARY;
    private static final int CONGRESS_START_DAY = 3;

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
     * supplemental list provided via configuration. When no numbers are supplied, the current Congress is estimated
     * from today's date so the ingest service continues to function without manual updates.
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
            congresses.add(estimateCurrentCongress());
        }
        return List.copyOf(congresses);
    }

    private int estimateCurrentCongress() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (today.getYear() <= FIRST_CONGRESS_YEAR) {
            return FIRST_CONGRESS_NUMBER;
        }
        int congress = ((today.getYear() - FIRST_CONGRESS_YEAR) / 2) + FIRST_CONGRESS_NUMBER;
        if (today.getYear() % 2 == 1
                && today.getMonth() == CONGRESS_START_MONTH
                && today.getDayOfMonth() < CONGRESS_START_DAY) {
            congress--;
        }
        return Math.max(FIRST_CONGRESS_NUMBER, congress);
    }
}
