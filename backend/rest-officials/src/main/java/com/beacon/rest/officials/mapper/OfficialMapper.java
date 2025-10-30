package com.beacon.rest.officials.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.common.accountability.v1.AttendanceSummary;
import com.beacon.common.accountability.v1.AttendanceSnapshot;
import com.beacon.rest.officials.model.AttendanceSnapshotResponse;
import com.beacon.rest.officials.model.AttendanceSummaryResponse;
import com.beacon.rest.officials.model.OfficialDetail;
import com.beacon.rest.officials.model.OfficialSummary;
import com.google.protobuf.Timestamp;

/**
 * Transforms persisted {@link PublicOfficial} records into REST friendly
 * response objects.
 */
public final class OfficialMapper {

    private OfficialMapper() {
    }

    public static OfficialSummary toSummary(PublicOfficial official) {
        return new OfficialSummary(
                official.getUuid(),
                official.getSourceId(),
                official.getFullName(),
                official.getPartyAffiliation(),
                official.getRoleTitle(),
                official.getPhotoUrl(),
                attendanceScoreOrNull(official, AttendanceSummary::getPresenceScore),
                attendanceScoreOrNull(official, AttendanceSummary::getParticipationScore),
                toInstant(official.getLastRefreshedAt()));
    }

    public static OfficialDetail toDetail(PublicOfficial official) {
        AttendanceSummaryResponse summaryResponse = official.hasAttendanceSummary()
                ? toSummaryResponse(official.getAttendanceSummary())
                : null;
        List<AttendanceSnapshotResponse> historyResponse = official.getAttendanceHistoryList().stream()
                .map(OfficialMapper::toSnapshotResponse)
                .collect(Collectors.toList());
        return new OfficialDetail(
                official.getUuid(),
                official.getSourceId(),
                official.getLegislativeBodyUuid(),
                official.getFullName(),
                official.getPartyAffiliation(),
                official.getRoleTitle(),
                official.getJurisdictionRegionCode(),
                official.getDistrictIdentifier(),
                toInstant(official.getTermStartDate()),
                toInstant(official.getTermEndDate()),
                official.getOfficeStatus().name(),
                official.getBiographyUrl(),
                official.getPhotoUrl(),
                official.getVersionHash(),
                toInstant(official.getLastRefreshedAt()),
                summaryResponse,
                historyResponse);
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || Objects.equals(timestamp, Timestamp.getDefaultInstance())) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Integer attendanceScoreOrNull(PublicOfficial official, java.util.function.ToIntFunction<AttendanceSummary> extractor) {
        if (!official.hasAttendanceSummary()) {
            return null;
        }
        return extractor.applyAsInt(official.getAttendanceSummary());
    }

    private static AttendanceSummaryResponse toSummaryResponse(AttendanceSummary summary) {
        return new AttendanceSummaryResponse(
                summary.getSessionsAttended(),
                summary.getSessionsTotal(),
                summary.getVotesParticipated(),
                summary.getVotesTotal(),
                summary.getPresenceScore(),
                summary.getParticipationScore());
    }

    private static AttendanceSnapshotResponse toSnapshotResponse(AttendanceSnapshot snapshot) {
        return new AttendanceSnapshotResponse(
                snapshot.getPeriodLabel(),
                toInstant(snapshot.getPeriodStart()),
                toInstant(snapshot.getPeriodEnd()),
                snapshot.getSessionsAttended(),
                snapshot.getSessionsTotal(),
                snapshot.getVotesParticipated(),
                snapshot.getVotesTotal(),
                snapshot.getPresenceScore(),
                snapshot.getParticipationScore());
    }
}
