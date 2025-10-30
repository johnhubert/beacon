package com.beacon.ingest.usafed.service;

import com.beacon.common.accountability.v1.AttendanceSnapshot;
import com.beacon.common.accountability.v1.AttendanceSummary;
import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.MemberVote;
import com.beacon.common.accountability.v1.OfficialAccountabilityEvent;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.common.accountability.v1.VotePosition;
import com.beacon.common.accountability.v1.VotingRecord;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientException;
import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.publisher.AccountabilityEventPublisher;
import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.VotingRecordRepository;
import com.beacon.stateful.mongo.VotingRecordRepository.PersistedVotingRecord;
import com.beacon.stateful.mongo.sync.RosterEntry;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService.SyncResult;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FederalIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederalIngestionService.class);
    private static final String LOCK_NAMESPACE = "legislative-roster";
    private static final String INGESTION_SOURCE = "congress.gov";
    private static final int ATTENDANCE_HISTORY_LIMIT = 24;

    private final AccountabilityEventPublisher publisher;
    private final CongressApiProperties properties;
    private final CongressGovClient congressGovClient;
    private final RosterSynchronizationService rosterSynchronizationService;
    private final PublicOfficialRepository publicOfficialRepository;
    private final LegislativeBodyRepository legislativeBodyRepository;
    private final VotingRecordRepository votingRecordRepository;

    public FederalIngestionService(
            AccountabilityEventPublisher publisher,
            CongressApiProperties properties,
            CongressGovClient congressGovClient,
            RosterSynchronizationService rosterSynchronizationService,
            PublicOfficialRepository publicOfficialRepository,
            LegislativeBodyRepository legislativeBodyRepository,
            VotingRecordRepository votingRecordRepository) {
        this.publisher = publisher;
        this.properties = properties;
        this.congressGovClient = congressGovClient;
        this.rosterSynchronizationService = rosterSynchronizationService;
        this.publicOfficialRepository = publicOfficialRepository;
        this.legislativeBodyRepository = legislativeBodyRepository;
        this.votingRecordRepository = votingRecordRepository;
    }

    public void refreshCongressRoster() {
        Duration refreshInterval = properties.pollInterval();
        LOGGER.info("Evaluating congressional roster freshness for {}th Congress", properties.congressNumber());

        try {
            List<LegislativeBody> bodies = congressGovClient.fetchLegislativeBodies(properties.congressNumber());
            for (LegislativeBody body : bodies) {
                Supplier<List<RosterEntry>> supplier = () -> toRosterEntries(body);
                SyncResult result = rosterSynchronizationService.synchronizeIfStale(
                        LOCK_NAMESPACE,
                        body,
                        refreshInterval,
                        supplier);
                handleSyncResult(body, result);
                refreshAttendanceMetrics(body);
            }
        } catch (CongressGovClientException ex) {
            LOGGER.warn(
                    "Congress.gov roster refresh skipped: {}. Verify CONGRESS_API_KEY or network access before retrying.",
                    ex.getMessage());
        } catch (Exception ex) {
            LOGGER.error("Unexpected failure while refreshing congressional roster", ex);
        }
    }

    private void handleSyncResult(LegislativeBody body, SyncResult result) {
        if (result.lockHeldByOther()) {
            LOGGER.debug("Another instance is refreshing {} (sourceId={}); skipping", body.getName(), body.getSourceId());
            return;
        }
        if (result.skipped()) {
            LOGGER.info(
                    "Skipping {} refresh; last run at {} is within interval",
                    body.getName(),
                    result.refreshedAt());
            return;
        }
        if (!result.refreshed()) {
            LOGGER.debug("No updates applied for {}", body.getName());
            return;
        }

        LOGGER.info(
                "Refreshed {} officials for {} ({} inserts, {} updates)",
                result.scanned(),
                body.getName(),
                result.insertedOfficials().size(),
                result.updatedOfficials().size());

        result.insertedOfficials().forEach(official -> publishRosterEvent(body, official, false));
        result.updatedOfficials().forEach(official -> publishRosterEvent(body, official, true));
    }

    private void refreshAttendanceMetrics(LegislativeBody body) {
        if (body.getChamberType() != ChamberType.LOWER) {
            return;
        }
        try {
            ingestHouseVotes(body);
        } catch (CongressGovClientException ex) {
            LOGGER.warn("Unable to compute attendance metrics for {}: {}", body.getName(), ex.getMessage());
        } catch (Exception ex) {
            LOGGER.error("Unexpected error computing attendance metrics for {}", body.getName(), ex);
        }
    }

    private boolean updateOfficialAttendance(LegislativeBody body, String sourceId, AttendanceStatisticsCalculator.AttendanceStatistics stats) {
        return publicOfficialRepository.findOfficialBySourceId(sourceId).map(official -> {
            AttendanceStatisticsCalculator.AttendanceCounters counters = stats.summary();
            AttendanceSummary.Builder summaryBuilder = AttendanceSummary.newBuilder()
                    .setSessionsAttended(counters.sessionsAttended())
                    .setSessionsTotal(counters.sessionsTotal())
                    .setVotesParticipated(counters.votesParticipated())
                    .setVotesTotal(counters.votesTotal())
                    .setPresenceScore(counters.presenceScore())
                    .setParticipationScore(counters.participationScore());

            PublicOfficial.Builder builder = official.toBuilder()
                    .setAttendanceSummary(summaryBuilder)
                    .clearAttendanceHistory();

            for (AttendanceStatisticsCalculator.AttendanceSnapshotData snapshot : stats.history()) {
                AttendanceStatisticsCalculator.AttendanceCounters periodCounters = snapshot.counters();
                AttendanceSnapshot.Builder snapshotBuilder = AttendanceSnapshot.newBuilder()
                        .setPeriodLabel(snapshot.periodLabel())
                        .setSessionsAttended(periodCounters.sessionsAttended())
                        .setSessionsTotal(periodCounters.sessionsTotal())
                        .setVotesParticipated(periodCounters.votesParticipated())
                        .setVotesTotal(periodCounters.votesTotal())
                        .setPresenceScore(periodCounters.presenceScore())
                        .setParticipationScore(periodCounters.participationScore());
                if (snapshot.periodStart() != null) {
                    snapshotBuilder.setPeriodStart(toTimestamp(snapshot.periodStart()));
                }
                if (snapshot.periodEnd() != null) {
                    snapshotBuilder.setPeriodEnd(toTimestamp(snapshot.periodEnd()));
                }
                builder.addAttendanceHistory(snapshotBuilder);
            }

            PublicOfficial updated = builder.build();
            publicOfficialRepository.upsertOfficial(updated);
            publishRosterEvent(body, updated, true);
            return true;
        }).orElse(false);
    }

    /**
     * Ensures the local vote cache contains the latest roll call data and refreshes attendance metrics after each vote
     * so downstream systems observe progress in real time.
     */
    private void ingestHouseVotes(LegislativeBody body) throws CongressGovClientException {
        int congressNumber = properties.congressNumber();
        List<CongressGovClient.HouseVoteSummary> pendingSummaries = new ArrayList<>();
        int totalSummaries = 0;
        Instant latestSummaryUpdate = null;

        List<PersistedVotingRecord> existingRecords = votingRecordRepository.findByLegislativeBody(body.getUuid(), 0);
        Map<String, Instant> existingRecordUpdates = new HashMap<>();
        Map<String, Boolean> existingRecordCompleteness = new HashMap<>();
        for (PersistedVotingRecord record : existingRecords) {
            existingRecordUpdates.put(record.votingRecord().getSourceId(), record.updateDateUtc());
            existingRecordCompleteness.put(record.votingRecord().getSourceId(), record.votingRecord().getMemberVotesCount() > 0);
            if (record.updateDateUtc() != null
                    && (latestSummaryUpdate == null || record.updateDateUtc().isAfter(latestSummaryUpdate))) {
                latestSummaryUpdate = record.updateDateUtc();
            }
        }

        for (int session = 1; session <= 2; session++) {
            List<CongressGovClient.HouseVoteSummary> summaries =
                    congressGovClient.fetchHouseVoteSummaries(congressNumber, session);
            totalSummaries += summaries.size();
            for (CongressGovClient.HouseVoteSummary summary : summaries) {
                Instant updateCandidate = summary.updateDate() != null ? summary.updateDate() : summary.startDate();
                if (updateCandidate != null && (latestSummaryUpdate == null || updateCandidate.isAfter(latestSummaryUpdate))) {
                    latestSummaryUpdate = updateCandidate;
                }
                String voteSourceId = buildHouseVoteSourceId(body, summary.sessionNumber(), summary.rollCallNumber());
                Instant storedUpdate = existingRecordUpdates.get(voteSourceId);
                boolean hasMemberVotes = existingRecordCompleteness.getOrDefault(voteSourceId, false);
                if (storedUpdate != null
                        && updateCandidate != null
                        && !updateCandidate.isAfter(storedUpdate)
                        && hasMemberVotes) {
                    continue;
                }
                pendingSummaries.add(summary);
            }
        }

        int cachedCount = totalSummaries - pendingSummaries.size();
        LOGGER.info(
                "House vote ingestion sweep for {}: {} summaries discovered ({} cached, {} to fetch)",
                body.getName(),
                totalSummaries,
                cachedCount,
                pendingSummaries.size());

        Map<String, Optional<String>> officialUuidCache = new HashMap<>();
        int ingestedCount = 0;
        int failures = 0;
        Instant latestProcessedUpdate = null;

        for (CongressGovClient.HouseVoteSummary summary : pendingSummaries) {
            try {
                Instant update = ingestHouseVote(body, congressNumber, summary, officialUuidCache);
                if (update != null && (latestProcessedUpdate == null || update.isAfter(latestProcessedUpdate))) {
                    latestProcessedUpdate = update;
                }
                ingestedCount++;

                Instant metricsUpdate = recomputeAttendanceFromRepository(
                        body,
                        "vote %d (session %d)".formatted(summary.rollCallNumber(), summary.sessionNumber()));
                if (metricsUpdate != null
                        && (latestProcessedUpdate == null || metricsUpdate.isAfter(latestProcessedUpdate))) {
                    latestProcessedUpdate = metricsUpdate;
                }
            } catch (CongressGovClientException ex) {
                failures++;
                LOGGER.warn(
                        "House vote {} (session {}) for {} skipped due to upstream error: {}",
                        summary.rollCallNumber(),
                        summary.sessionNumber(),
                        body.getName(),
                        ex.getMessage());
            } catch (Exception ex) {
                failures++;
                LOGGER.error(
                        "Unexpected error while caching House vote {} (session {}) for {}",
                        summary.rollCallNumber(),
                        summary.sessionNumber(),
                        body.getName(),
                        ex);
            }
        }

        if (pendingSummaries.isEmpty()) {
            LOGGER.debug("House vote cache for {} already up to date ({} stored)", body.getName(), cachedCount);
            return;
        }

        LOGGER.info(
                "House vote ingestion for {} completed: {} new votes cached, {} already cached, {} failures",
                body.getName(),
                ingestedCount,
                cachedCount,
                failures);
    }

    /**
     * Downloads the detailed roll call payload, maps it to our common format, and writes it to Mongo so we avoid
     * repeating a slow upstream request on the next run.
     */
    private Instant ingestHouseVote(
            LegislativeBody body,
            int congressNumber,
            CongressGovClient.HouseVoteSummary summary,
            Map<String, Optional<String>> officialUuidCache) throws CongressGovClientException {
        CongressGovClient.HouseVoteDetail detail = congressGovClient.fetchHouseVoteDetail(
                congressNumber,
                summary.sessionNumber(),
                summary.rollCallNumber());
        LOGGER.debug(
                "House vote {} session {} returned {} member votes",
                detail.rollCallNumber(),
                detail.sessionNumber(),
                detail.memberVotes().size());

        VotingRecord votingRecord = buildVotingRecord(body, congressNumber, summary, detail, officialUuidCache);
        Instant updateDate = detail.updateDate() != null
                ? detail.updateDate()
                : (summary.updateDate() != null ? summary.updateDate() : summary.startDate());

        PersistedVotingRecord persisted = new PersistedVotingRecord(
                votingRecord,
                updateDate,
                congressNumber,
                detail.sessionNumber(),
                detail.rollCallNumber(),
                detail.sourceDataUrl(),
                detail.result(),
                detail.voteType(),
                detail.legislationType(),
                detail.legislationNumber(),
                detail.legislationUrl());
        votingRecordRepository.upsert(persisted);
        LOGGER.debug(
                "Cached House vote congress {} session {} roll call {} for {}",
                congressNumber,
                persisted.sessionNumber(),
                persisted.rollCallNumber(),
                body.getName());
        return persisted.updateDateUtc();
    }

    private VotingRecord buildVotingRecord(
            LegislativeBody body,
            int congressNumber,
            CongressGovClient.HouseVoteSummary summary,
            CongressGovClient.HouseVoteDetail detail,
            Map<String, Optional<String>> officialUuidCache) {
        String sourceId = buildHouseVoteSourceId(body, detail.sessionNumber(), detail.rollCallNumber());
        String voteUuid = deterministicUuid("house-vote-" + sourceId);

        VotingRecord.Builder builder = VotingRecord.newBuilder()
                .setUuid(voteUuid)
                .setSourceId(sourceId)
                .setLegislativeBodyUuid(body.getUuid())
                .setSubjectSummary(Optional.ofNullable(detail.question()).orElse(""))
                .setBillReference(buildBillReference(detail.legislationType(), detail.legislationNumber()))
                .setBillUri(Optional.ofNullable(detail.legislationUrl()).orElse(""))
                .setRollCallReference(buildRollCallReference(congressNumber, detail.sessionNumber(), detail.rollCallNumber()));

        Instant voteDate = detail.startDate() != null ? detail.startDate() : summary.startDate();
        if (voteDate != null) {
            builder.setVoteDateUtc(toTimestamp(voteDate));
        }

        detail.memberVotes().forEach((bioguideId, voteResult) -> {
            if (bioguideId == null || bioguideId.isBlank()) {
                return;
            }
            String voteCast = voteResult.voteCast();
            VotePosition position = mapVotePosition(voteCast);
            MemberVote.Builder voteBuilder = MemberVote.newBuilder()
                    .setUuid(deterministicUuid("house-vote-member-" + sourceId + "-" + bioguideId))
                    .setSourceId(bioguideId)
                    .setVotingRecordUuid(voteUuid)
                    .setVotePosition(position)
                    .setGroupPosition("")
                    .setNotes(Optional.ofNullable(voteCast).orElse(""));
            Optional<String> officialUuid = officialUuidCache.computeIfAbsent(
                    bioguideId,
                    key -> publicOfficialRepository.findOfficialBySourceId(key).map(PublicOfficial::getUuid));
            officialUuid.filter(uuid -> !uuid.isBlank()).ifPresent(voteBuilder::setOfficialUuid);
            builder.addMemberVotes(voteBuilder.build());
        });

        return builder.build();
    }

    private Instant recomputeAttendanceFromRepository(LegislativeBody body, String context) {
        List<PersistedVotingRecord> persistedRecords = votingRecordRepository.findByLegislativeBody(body.getUuid(), 0);
        if (persistedRecords.isEmpty()) {
            LOGGER.debug("Skipping attendance metrics recompute for {} [{}]: no vote records cached", body.getName(), context);
            return null;
        }
        List<AttendanceStatisticsCalculator.VoteRecord> voteRecords = new ArrayList<>(persistedRecords.size());
        Instant latestUpdate = null;
        for (PersistedVotingRecord record : persistedRecords) {
            voteRecords.add(toAttendanceVoteRecord(record));
            if (record.updateDateUtc() != null
                    && (latestUpdate == null || record.updateDateUtc().isAfter(latestUpdate))) {
                latestUpdate = record.updateDateUtc();
            }
        }
        applyAttendanceMetrics(body, voteRecords, latestUpdate, context);
        return latestUpdate;
    }

    /**
     * Recomputes attendance metrics for all officials belonging to the supplied body using the supplied vote records.
     *
     * @param body legislative body whose officials should be updated
     * @param voteRecords cached vote records to aggregate
     * @param latestUpdate timestamp of the most recent roll call processed (can be {@code null})
     * @param context label used for logging (e.g., chunk identifier)
     */
    private void applyAttendanceMetrics(
            LegislativeBody body,
            List<AttendanceStatisticsCalculator.VoteRecord> voteRecords,
            Instant latestUpdate,
            String context) {
        if (voteRecords == null || voteRecords.isEmpty()) {
            return;
        }

        AttendanceStatisticsCalculator.AttendanceComputation computation =
                AttendanceStatisticsCalculator.compute(voteRecords, ATTENDANCE_HISTORY_LIMIT);

        Instant effectiveUpdate = latestUpdate;
        if (computation.latestUpdate() != null
                && (effectiveUpdate == null || computation.latestUpdate().isAfter(effectiveUpdate))) {
            effectiveUpdate = computation.latestUpdate();
        }

        if (computation.statisticsByMember().isEmpty()) {
            if (effectiveUpdate != null) {
                legislativeBodyRepository.updateLastVoteIngestedAt(body.getSourceId(), effectiveUpdate);
            }
            LOGGER.debug("No attendance updates required for {} [{}]; metrics already current", body.getName(), context);
            return;
        }

        int updatedOfficials = 0;
        for (Map.Entry<String, AttendanceStatisticsCalculator.AttendanceStatistics> entry : computation.statisticsByMember().entrySet()) {
            if (updateOfficialAttendance(body, entry.getKey(), entry.getValue())) {
                updatedOfficials++;
            }
        }
        if (effectiveUpdate != null) {
            legislativeBodyRepository.updateLastVoteIngestedAt(body.getSourceId(), effectiveUpdate);
        }
        LOGGER.info(
                "Computed attendance metrics for {} [{}]: {} vote records processed, {} officials updated",
                body.getName(),
                context,
                computation.voteRecordsProcessed(),
                updatedOfficials);
    }

    private String buildBillReference(String legislationType, String legislationNumber) {
        if (legislationType == null && legislationNumber == null) {
            return "";
        }
        String type = legislationType == null ? "" : legislationType.trim();
        String number = legislationNumber == null ? "" : legislationNumber.trim();
        if (type.isEmpty() && number.isEmpty()) {
            return "";
        }
        if (type.isEmpty()) {
            return number;
        }
        if (number.isEmpty()) {
            return type;
        }
        return "%s %s".formatted(type, number);
    }

    private String buildRollCallReference(int congressNumber, int sessionNumber, int rollCallNumber) {
        return "%d-%d-%d".formatted(congressNumber, sessionNumber, rollCallNumber);
    }

    private String buildHouseVoteSourceId(LegislativeBody body, int sessionNumber, int rollCallNumber) {
        return "%s-S%02d-R%03d".formatted(body.getSourceId(), sessionNumber, rollCallNumber);
    }

    private VotePosition mapVotePosition(String voteCast) {
        if (voteCast == null) {
            return VotePosition.VOTE_POSITION_UNSPECIFIED;
        }
        String normalized = voteCast.trim().toUpperCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('/', ' ')
                .replaceAll("\\s+", " ");
        return switch (normalized) {
            case "YEA", "AYE", "YES", "YEA AND NAY", "AYE AND NAY" -> VotePosition.YEA;
            case "NAY", "NO" -> VotePosition.NAY;
            case "ABSENT" -> VotePosition.ABSENT;
            case "PRESENT", "NOT VOTING", "PRESENT NOT VOTING" -> VotePosition.NOT_VOTING;
            default -> VotePosition.VOTE_POSITION_UNSPECIFIED;
        };
    }

    private AttendanceStatisticsCalculator.VoteRecord toAttendanceVoteRecord(PersistedVotingRecord record) {
        Map<String, String> memberVotes = new LinkedHashMap<>();
        for (MemberVote vote : record.votingRecord().getMemberVotesList()) {
            String bioguideId = vote.getSourceId();
            if (bioguideId == null || bioguideId.isBlank()) {
                continue;
            }
            String label = vote.getNotes();
            if (label == null || label.isBlank()) {
                label = vote.getVotePosition().name();
            }
            memberVotes.put(bioguideId, label);
        }
        Instant startDate = record.votingRecord().hasVoteDateUtc()
                ? Instant.ofEpochSecond(
                        record.votingRecord().getVoteDateUtc().getSeconds(),
                        record.votingRecord().getVoteDateUtc().getNanos())
                : null;
        return new AttendanceStatisticsCalculator.VoteRecord(startDate, record.updateDateUtc(), memberVotes);
    }

    private static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private List<RosterEntry> toRosterEntries(LegislativeBody body) {
        return congressGovClient.fetchMemberListings(properties.congressNumber(), body.getChamberType()).stream()
                .map(listing -> new RosterEntry(listing.publicOfficial(), listing.sourceJson() == null ? "" : listing.sourceJson()))
                .toList();
    }

    private void publishRosterEvent(LegislativeBody body, PublicOfficial official, boolean existing) {
        try {
            OfficialAccountabilityEvent event = OfficialAccountabilityEvent.newBuilder()
                    .setUuid(UUID.randomUUID().toString())
                    .setSourceId("congress-roster-" + official.getSourceId())
                    .setCapturedAt(toTimestamp(Instant.now()))
                    .setIngestionSource(INGESTION_SOURCE)
                    .setPartitionKey(buildPartitionKey(official.getUuid(), body))
                    .setLegislativeBody(body)
                    .setPublicOfficial(official)
                    .build();
            publisher.publish(event);
            LOGGER.debug("Published roster {} event for {}", existing ? "update" : "insert", official.getSourceId());
        } catch (Exception ex) {
            LOGGER.warn("Failed to publish roster event for {}", official.getSourceId(), ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private String buildPartitionKey(String officialUuid, LegislativeBody body) {
        String chamber = body.getChamberType().name().toLowerCase(Locale.ROOT);
        return "%s::%s".formatted(officialUuid, chamber);
    }
}
