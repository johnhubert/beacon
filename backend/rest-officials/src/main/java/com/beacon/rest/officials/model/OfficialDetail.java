package com.beacon.rest.officials.model;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detailed view of an official used by the profile screen.
 */
@Schema(name = "OfficialDetail", description = "Detailed public profile of an official.")
public record OfficialDetail(
        @Schema(description = "Stable Beacon UUID for the official", example = "d6f4a0f9-0b8a-4a62-a6c8-e2f301cb8eb7")
        String uuid,
        @Schema(description = "Source identifier from the upstream system", example = "A000360")
        String sourceId,
        @Schema(description = "Legislative body UUID the official belongs to", example = "5c7971df-1922-4fd8-8bda-1a7a322a5409")
        String legislativeBodyUuid,
        @Schema(description = "Display name of the official", example = "Jane Doe")
        String fullName,
        @Schema(description = "Party affiliation if provided", example = "Independent")
        String partyAffiliation,
        @Schema(description = "Role or office title", example = "Representative for NY-12")
        String roleTitle,
        @Schema(description = "Jurisdiction or region code for the current office", example = "NY")
        String jurisdictionRegionCode,
        @Schema(description = "District identifier when applicable", example = "12")
        String districtIdentifier,
        @Schema(description = "Term start date", type = "string", format = "date-time")
        Instant termStartDate,
        @Schema(description = "Term end date", type = "string", format = "date-time")
        Instant termEndDate,
        @Schema(description = "Status of the office, e.g., ACTIVE or VACANT", example = "ACTIVE")
        String officeStatus,
        @Schema(description = "External biography URL", example = "https://example.com/bio/jane-doe")
        String biographyUrl,
        @Schema(description = "Portrait image URL", example = "https://example.com/photos/jane.jpg")
        String photoUrl,
        @Schema(description = "Version hash of the source payload for change detection", example = "9c54f7a3b0e62f43")
        String versionHash,
        @Schema(description = "Timestamp of the last successful refresh", type = "string", format = "date-time")
        Instant lastRefreshedAt,
        @Schema(description = "Aggregated presence and participation scores for the official")
        AttendanceSummaryResponse attendanceSummary,
        @Schema(description = "Per-period attendance history used for sparklines")
        List<AttendanceSnapshotResponse> attendanceHistory) {
}
