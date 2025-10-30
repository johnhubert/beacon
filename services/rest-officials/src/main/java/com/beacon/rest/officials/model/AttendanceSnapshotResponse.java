package com.beacon.rest.officials.model;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-period attendance/participation metrics exposed to the UI for sparklines and drill-downs.
 */
@Schema(name = "AttendanceSnapshot", description = "Attendance metrics for a specific period.")
public record AttendanceSnapshotResponse(
        @Schema(description = "Period label such as a calendar month or session identifier", example = "2025-01")
        String periodLabel,
        @Schema(description = "Start timestamp for the period", type = "string", format = "date-time")
        Instant periodStart,
        @Schema(description = "End timestamp for the period", type = "string", format = "date-time")
        Instant periodEnd,
        @Schema(description = "Sessions attended during the period", example = "5")
        int sessionsAttended,
        @Schema(description = "Total sessions tracked in the period", example = "6")
        int sessionsTotal,
        @Schema(description = "Votes participated in during the period", example = "5")
        int votesParticipated,
        @Schema(description = "Total votes tracked in the period", example = "6")
        int votesTotal,
        @Schema(description = "Presence score for the period", example = "83")
        int presenceScore,
        @Schema(description = "Participation score for the period", example = "92")
        int participationScore) {
}
