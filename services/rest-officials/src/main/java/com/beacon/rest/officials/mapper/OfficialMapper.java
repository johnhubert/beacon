package com.beacon.rest.officials.mapper;

import java.time.Instant;
import java.util.Objects;

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
        Integer presenceScore = null;
        Integer activityScore = null;
        Integer overallScore = null;
        if (official.hasAttendanceSummary()) {
            AttendanceSummary summary = official.getAttendanceSummary();
            presenceScore = summary.getPresenceScore();
            activityScore = summary.getParticipationScore();
            overallScore = calculateOverallScore(presenceScore, activityScore);
        }
        return new OfficialSummary(
                official.getUuid(),
                official.getSourceId(),
                official.getFullName(),
                official.getPartyAffiliation(),
                official.getRoleTitle(),
                official.getPhotoUrl(),
                presenceScore,
                activityScore,
                overallScore,
                toInstant(official.getLastRefreshedAt()));
    }

    public static OfficialDetail toDetail(PublicOfficial official) {
        AttendanceSummaryResponse summaryResponse = official.hasAttendanceSummary()
                ? toSummaryResponse(official.getAttendanceSummary())
                : null;
        Integer presenceScore = null;
        Integer activityScore = null;
        Integer overallScore = null;
        if (summaryResponse != null) {
            presenceScore = summaryResponse.presenceScore();
            activityScore = summaryResponse.participationScore();
            overallScore = summaryResponse.overallScore();
        }
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
                presenceScore,
                activityScore,
                overallScore,
                official.getVersionHash(),
                toInstant(official.getLastRefreshedAt()),
                summaryResponse);
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || Objects.equals(timestamp, Timestamp.getDefaultInstance())) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static AttendanceSummaryResponse toSummaryResponse(AttendanceSummary summary) {
        int overallScore = calculateOverallScore(summary.getPresenceScore(), summary.getParticipationScore());
        return new AttendanceSummaryResponse(
                summary.getSessionsAttended(),
                summary.getSessionsTotal(),
                summary.getVotesParticipated(),
                summary.getVotesTotal(),
                summary.getPresenceScore(),
                summary.getParticipationScore(),
                overallScore);
    }

    public static AttendanceSnapshotResponse toSnapshotResponse(AttendanceSnapshot snapshot) {
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

    private static int calculateOverallScore(int presenceScore, int participationScore) {
        return Math.round((presenceScore + participationScore) / 2.0f);
    }
}
