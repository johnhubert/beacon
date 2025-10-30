package com.beacon.rest.officials.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated attendance and participation metrics for a public official.
 */
@Schema(name = "AttendanceSummary", description = "Aggregated attendance and participation metrics.")
public record AttendanceSummaryResponse(
        @Schema(description = "Sessions attended by the official", example = "42")
        int sessionsAttended,
        @Schema(description = "Total sessions tracked for the official", example = "48")
        int sessionsTotal,
        @Schema(description = "Ballots or roll calls the official voted on", example = "39")
        int votesParticipated,
        @Schema(description = "Total ballots or roll calls tracked for the official", example = "48")
        int votesTotal,
        @Schema(description = "Presence score rounded to the nearest whole number", example = "88")
        int presenceScore,
        @Schema(description = "Participation score rounded to the nearest whole number", example = "81")
        int participationScore) {
}
