package com.beacon.ingest.usafed.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CongressApiPropertiesTest {

    @Test
    void rosterCongressesIncludesConfiguredValuesInDescendingOrder() {
        CongressApiProperties properties = new CongressApiProperties(
                URI.create("https://example.com"),
                "key",
                Duration.ofHours(1),
                "Senate",
                118,
                Arrays.asList(117, 118, 0, -1, null));

        List<Integer> rosterCongresses = properties.rosterCongresses();

        assertEquals(List.of(118, 117), rosterCongresses);
    }
}
