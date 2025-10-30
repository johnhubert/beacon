package com.beacon.ingest.usafed.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility that aggregates member vote participation into cumulative and per-period counters.
 */
final class AttendanceStatisticsCalculator {

    private AttendanceStatisticsCalculator() {
    }

    static AttendanceComputation compute(List<VoteRecord> votes, int periodHistoryLimit) {
        Map<String, Map<YearMonth, PeriodCounter>> perPeriod = new HashMap<>();
        Instant latestUpdate = null;
        int voteRecordCount = 0;

        for (VoteRecord vote : votes) {
            if (vote == null || vote.memberVotes().isEmpty()) {
                continue;
            }
            voteRecordCount++;
            if (vote.updateDate() != null && (latestUpdate == null || vote.updateDate().isAfter(latestUpdate))) {
                latestUpdate = vote.updateDate();
            }
            YearMonth period = vote.startDate() == null
                    ? YearMonth.now(ZoneOffset.UTC)
                    : YearMonth.from(vote.startDate().atZone(ZoneOffset.UTC));
            for (Map.Entry<String, String> entry : vote.memberVotes().entrySet()) {
                String memberId = entry.getKey();
                if (memberId == null || memberId.isBlank()) {
                    continue;
                }
                String voteCast = entry.getValue();
                Map<YearMonth, PeriodCounter> memberPeriods = perPeriod.computeIfAbsent(memberId, key -> new LinkedHashMap<>());
                PeriodCounter periodCounter = memberPeriods.computeIfAbsent(period, key -> new PeriodCounter(key));
                periodCounter.record(voteCast, vote.startDate());
            }
        }

        Map<String, AttendanceStatistics> results = new HashMap<>();
        for (Map.Entry<String, Map<YearMonth, PeriodCounter>> entry : perPeriod.entrySet()) {
            String memberId = entry.getKey();
            Map<YearMonth, PeriodCounter> periodCounters = entry.getValue();
            List<YearMonth> periods = periodCounters.keySet().stream()
                    .sorted()
                    .collect(Collectors.toList());

            int sessionsTotal = periods.size();
            int sessionsAttended = 0;
            int votesParticipated = 0;
            int votesTotal = 0;

            List<AttendanceSnapshotData> history = new ArrayList<>();
            for (YearMonth period : periods) {
                PeriodCounter counter = periodCounters.get(period);
                AttendanceSnapshotData snapshot = counter.snapshot();
                AttendanceCounters counters = snapshot.counters();
                if (counters.sessionsAttended() > 0) {
                    sessionsAttended++;
                }
                votesParticipated += counters.votesParticipated();
                votesTotal += counters.votesTotal();
                history.add(snapshot);
            }

            if (periodHistoryLimit > 0 && history.size() > periodHistoryLimit) {
                history = history.subList(history.size() - periodHistoryLimit, history.size());
            }

            int presenceScore = sessionsTotal == 0 ? 0 : Math.round((sessionsAttended * 100f) / sessionsTotal);
            int participationScore = votesTotal == 0 ? 0 : Math.round((votesParticipated * 100f) / votesTotal);
            AttendanceCounters summary = new AttendanceCounters(sessionsAttended, sessionsTotal, votesParticipated, votesTotal, presenceScore, participationScore);
            results.put(memberId, new AttendanceStatistics(summary, history));
        }

        return new AttendanceComputation(results, latestUpdate, voteRecordCount);
    }

    static final class VoteRecord {
        private final Instant startDate;
        private final Instant updateDate;
        private final Map<String, String> memberVotes;

        VoteRecord(Instant startDate, Instant updateDate, Map<String, String> memberVotes) {
            this.startDate = startDate;
            this.updateDate = updateDate;
            this.memberVotes = memberVotes == null ? Map.of() : Map.copyOf(memberVotes);
        }

        Instant startDate() {
            return startDate;
        }

        Instant updateDate() {
            return updateDate;
        }

        Map<String, String> memberVotes() {
            return memberVotes;
        }
    }

    record AttendanceComputation(Map<String, AttendanceStatistics> statisticsByMember, Instant latestUpdate, int voteRecordsProcessed) {}

    record AttendanceStatistics(AttendanceCounters summary, List<AttendanceSnapshotData> history) {}

    record AttendanceSnapshotData(String periodLabel,
                                  Instant periodStart,
                                  Instant periodEnd,
                                  AttendanceCounters counters) {}

    record AttendanceCounters(int sessionsAttended,
                              int sessionsTotal,
                              int votesParticipated,
                              int votesTotal,
                              int presenceScore,
                              int participationScore) {}

    private static final class PeriodCounter {
        private final YearMonth period;
        private Instant minInstant;
        private Instant maxInstant;
        private int votesParticipated;
        private int votesTotal;
        private boolean present;

        PeriodCounter(YearMonth period) {
            this.period = Objects.requireNonNull(period, "period");
        }

        void record(String voteCast, Instant occurrence) {
            votesTotal++;
            if (isParticipatory(voteCast)) {
                votesParticipated++;
            }
            if (isPresent(voteCast)) {
                present = true;
            }
            if (occurrence != null) {
                if (minInstant == null || occurrence.isBefore(minInstant)) {
                    minInstant = occurrence;
                }
                if (maxInstant == null || occurrence.isAfter(maxInstant)) {
                    maxInstant = occurrence;
                }
            }
        }

        AttendanceSnapshotData snapshot() {
            int sessionsAttended = present ? 1 : 0;
            int sessionsTotal = 1;
            int presenceScore = present ? 100 : 0;
            int participationScore = votesTotal == 0 ? 0 : Math.round((votesParticipated * 100f) / votesTotal);
            AttendanceCounters counters = new AttendanceCounters(
                    sessionsAttended,
                    sessionsTotal,
                    votesParticipated,
                    votesTotal,
                    presenceScore,
                    participationScore);
            Instant start = minInstant != null
                    ? minInstant
                    : period.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = maxInstant != null
                    ? maxInstant
                    : period.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(1);
            String label = "%04d-%02d".formatted(period.getYear(), period.getMonthValue());
            return new AttendanceSnapshotData(label, start, end, counters);
        }

        YearMonth getPeriod() {
            return period;
        }
    }

    private static boolean isPresent(String voteCast) {
        if (voteCast == null) {
            return false;
        }
        String normalized = voteCast.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return !normalized.contains("NOT VOTING");
    }

    private static boolean isParticipatory(String voteCast) {
        if (voteCast == null) {
            return false;
        }
        String normalized = voteCast.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "YEA", "NAY", "AYE", "NO", "YEA AND NAY", "AYE AND NAY" -> true;
            default -> false;
        };
    }
}
