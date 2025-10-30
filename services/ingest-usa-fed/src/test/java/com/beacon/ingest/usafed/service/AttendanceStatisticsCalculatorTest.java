package com.beacon.ingest.usafed.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttendanceStatisticsCalculatorTest {

    @Test
    void computeAggregatesPerMemberAndPeriod() {
        Instant vote1Time = Instant.parse("2025-01-01T15:00:00Z");
        Instant vote2Time = Instant.parse("2025-01-05T18:00:00Z");
        Instant vote3Time = Instant.parse("2025-02-15T17:00:00Z");

        List<AttendanceStatisticsCalculator.VoteRecord> votes = List.of(
                new AttendanceStatisticsCalculator.VoteRecord(
                        vote1Time,
                        vote1Time,
                        Map.of(
                                "A000001", "Yea",
                                "B000001", "Not Voting")),
                new AttendanceStatisticsCalculator.VoteRecord(
                        vote2Time,
                        vote2Time,
                        Map.of(
                                "A000001", "Nay",
                                "B000001", "Yea")),
                new AttendanceStatisticsCalculator.VoteRecord(
                        vote3Time,
                        vote3Time,
                        Map.of(
                                "A000001", "Not Voting",
                                "B000001", "Not Voting"))
        );

        AttendanceStatisticsCalculator.AttendanceComputation computation = AttendanceStatisticsCalculator.compute(votes, 12);

        assertThat(computation.latestUpdate()).isEqualTo(vote3Time);
        assertThat(computation.voteRecordsProcessed()).isEqualTo(3);
        assertThat(computation.statisticsByMember()).hasSize(2);

        AttendanceStatisticsCalculator.AttendanceStatistics memberA = computation.statisticsByMember().get("A000001");
        assertThat(memberA).isNotNull();
        assertThat(memberA.summary().sessionsAttended()).isEqualTo(1);
        assertThat(memberA.summary().sessionsTotal()).isEqualTo(2);
        assertThat(memberA.summary().votesParticipated()).isEqualTo(2);
        assertThat(memberA.summary().votesTotal()).isEqualTo(3);
        assertThat(memberA.summary().presenceScore()).isEqualTo(50);
        assertThat(memberA.summary().participationScore()).isEqualTo(67);
        assertThat(memberA.history()).hasSize(2);

        AttendanceStatisticsCalculator.AttendanceStatistics memberB = computation.statisticsByMember().get("B000001");
        assertThat(memberB).isNotNull();
        assertThat(memberB.summary().sessionsAttended()).isEqualTo(1);
        assertThat(memberB.summary().sessionsTotal()).isEqualTo(2);
        assertThat(memberB.summary().votesParticipated()).isEqualTo(1);
        assertThat(memberB.summary().votesTotal()).isEqualTo(3);
        assertThat(memberB.summary().presenceScore()).isEqualTo(50);
        assertThat(memberB.summary().participationScore()).isEqualTo(33);
        assertThat(memberB.history()).hasSize(2);
    }
}
