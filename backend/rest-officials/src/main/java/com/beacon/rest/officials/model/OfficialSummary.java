package com.beacon.rest.officials.model;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight representation returned by the list endpoint.
 */
@Schema(name = "OfficialSummary", description = "Summary view of a public official.")
public record OfficialSummary(
        @Schema(description = "Stable Beacon UUID for the official", example = "d6f4a0f9-0b8a-4a62-a6c8-e2f301cb8eb7")
        String uuid,
        @Schema(description = "Source identifier, for example a Bioguide ID", example = "A000360")
        String sourceId,
        @Schema(description = "Display name of the official", example = "Jane Doe")
        String fullName,
        @Schema(description = "Party affiliation if provided", example = "Independent")
        String partyAffiliation,
        @Schema(description = "Role or office title", example = "Representative for NY-12")
        String roleTitle,
        @Schema(description = "URL to the official portrait when available", example = "https://example.com/photos/jane.jpg")
        String photoUrl,
        @Schema(description = "Presence score rounded to a whole number between 0 and 100", example = "92")
        Integer presenceScore,
        @Schema(description = "Participation score rounded to a whole number between 0 and 100", example = "88")
        Integer participationScore,
        @Schema(description = "Timestamp of the last successful refresh", type = "string", format = "date-time")
        Instant lastRefreshedAt) {
}
