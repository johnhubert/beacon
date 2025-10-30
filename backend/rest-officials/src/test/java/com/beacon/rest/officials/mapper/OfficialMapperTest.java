package com.beacon.rest.officials.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.common.accountability.v1.AttendanceSnapshot;
import com.beacon.common.accountability.v1.AttendanceSummary;
import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.rest.officials.model.AttendanceSnapshotResponse;
import com.beacon.rest.officials.model.AttendanceSummaryResponse;
import com.beacon.rest.officials.model.OfficialDetail;
import com.beacon.rest.officials.model.OfficialSummary;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialMapperTest {

    @Test
    void mapsAttendanceMetricsIntoSummaryAndDetail() {
        AttendanceSummary attendanceSummary = AttendanceSummary.newBuilder()
                .setSessionsAttended(8)
                .setSessionsTotal(10)
                .setVotesParticipated(7)
                .setVotesTotal(10)
                .setPresenceScore(80)
                .setParticipationScore(70)
                .build();
        AttendanceSnapshot snapshot = AttendanceSnapshot.newBuilder()
                .setPeriodLabel("2025-01")
                .setPeriodStart(toTimestamp(Instant.parse("2025-01-01T00:00:00Z")))
                .setPeriodEnd(toTimestamp(Instant.parse("2025-01-31T23:59:59Z")))
                .setSessionsAttended(5)
                .setSessionsTotal(6)
                .setVotesParticipated(5)
                .setVotesTotal(6)
                .setPresenceScore(83)
                .setParticipationScore(83)
                .build();

        PublicOfficial official = PublicOfficial.newBuilder()
                .setUuid("uuid-1")
                .setSourceId("A000001")
                .setLegislativeBodyUuid("body-1")
                .setFullName("Sample Official")
                .setPartyAffiliation("Independent")
                .setRoleTitle("Representative")
                .setOfficeStatus(OfficeStatus.ACTIVE)
                .setBiographyUrl("https://example.com")
                .setPhotoUrl("https://example.com/photo.jpg")
                .setVersionHash("hash")
                .setAttendanceSummary(attendanceSummary)
                .addAttendanceHistory(snapshot)
                .build();

        OfficialSummary summary = OfficialMapper.toSummary(official);
        assertThat(summary.presenceScore()).isEqualTo(80);
        assertThat(summary.participationScore()).isEqualTo(70);

        OfficialDetail detail = OfficialMapper.toDetail(official);
        AttendanceSummaryResponse summaryResponse = detail.attendanceSummary();
        assertThat(summaryResponse.sessionsAttended()).isEqualTo(8);
        assertThat(summaryResponse.sessionsTotal()).isEqualTo(10);
        assertThat(summaryResponse.presenceScore()).isEqualTo(80);
        assertThat(summaryResponse.participationScore()).isEqualTo(70);

        List<AttendanceSnapshotResponse> history = detail.attendanceHistory();
        assertThat(history).hasSize(1);
        AttendanceSnapshotResponse snapshotResponse = history.get(0);
        assertThat(snapshotResponse.periodLabel()).isEqualTo("2025-01");
        assertThat(snapshotResponse.presenceScore()).isEqualTo(83);
        assertThat(snapshotResponse.participationScore()).isEqualTo(83);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
