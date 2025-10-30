package com.beacon.stateful.mongo.sync;

import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository.OfficialMetadata;
import com.beacon.stateful.mongo.lock.DistributedLockManager;
import com.google.protobuf.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestrates roster synchronization across legislative bodies while enforcing distributed
 * locking and change detection.
 */
public class RosterSynchronizationService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Duration LOCK_TTL_BUFFER = Duration.ofMinutes(5);

    private final PublicOfficialRepository publicOfficialRepository;
    private final LegislativeBodyRepository legislativeBodyRepository;
    private final DistributedLockManager lockManager;
    private final Clock clock;

    public RosterSynchronizationService(
            PublicOfficialRepository publicOfficialRepository,
            LegislativeBodyRepository legislativeBodyRepository,
            DistributedLockManager lockManager,
            Clock clock) {
        this.publicOfficialRepository = Objects.requireNonNull(publicOfficialRepository, "publicOfficialRepository");
        this.legislativeBodyRepository = Objects.requireNonNull(legislativeBodyRepository, "legislativeBodyRepository");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SyncResult synchronizeIfStale(
            String lockNamespace,
            LegislativeBody body,
            Duration refreshInterval,
            Supplier<List<RosterEntry>> rosterSupplier) {

        Instant now = clock.instant();
        Optional<Instant> bodyRefresh = legislativeBodyRepository.findRosterLastRefreshedAt(body.getSourceId());
        Optional<Instant> latestRefresh = bodyRefresh.isPresent()
                ? bodyRefresh
                : publicOfficialRepository.findLatestRefreshTimestamp(body.getUuid());
        if (latestRefresh.isPresent()
                && Duration.between(latestRefresh.get(), now).compareTo(refreshInterval) < 0) {
            return SyncResult.skipped(latestRefresh.get());
        }

        String lockKey = "%s:%s".formatted(lockNamespace, body.getSourceId());
        String token = UUID.randomUUID().toString();
        Duration lockTtl = refreshInterval.plus(LOCK_TTL_BUFFER);

        if (!lockManager.tryAcquire(lockKey, token, lockTtl)) {
            return SyncResult.locked();
        }

        try {
            List<RosterEntry> entries = rosterSupplier.get();
            if (entries == null) {
                entries = List.of();
            }
            SyncResult result = synchronizeEntries(body, entries, now);
            legislativeBodyRepository.updateRosterLastRefreshedAt(body.getSourceId(), now);
            return result;
        } finally {
            lockManager.release(lockKey, token);
        }
    }

    private SyncResult synchronizeEntries(LegislativeBody body, List<RosterEntry> entries, Instant timestamp) {
        legislativeBodyRepository.upsert(body);

        List<PublicOfficial> inserted = new ArrayList<>();
        List<PublicOfficial> updated = new ArrayList<>();
        int scanned = 0;

        for (RosterEntry entry : entries) {
            scanned++;
            PublicOfficial official = entry.official();
            String versionHash = computeVersionHash(entry.sourcePayload());
            Optional<OfficialMetadata> metadata = publicOfficialRepository.findMetadataBySourceId(official.getSourceId());
            if (metadata.isPresent() && Objects.equals(metadata.get().versionHash(), versionHash)) {
                continue;
            }

            PublicOfficial.Builder builder = official.toBuilder()
                    .setVersionHash(versionHash)
                    .setLastRefreshedAt(toTimestamp(timestamp))
                    .clearUuid();

            metadata
                    .map(OfficialMetadata::uuid)
                    .filter(uuid -> uuid != null && !uuid.isBlank())
                    .ifPresentOrElse(
                            builder::setUuid,
                            () -> builder.setUuid(official.getUuid()));

            PublicOfficial updatedOfficial = builder.build();
            publicOfficialRepository.upsertOfficial(updatedOfficial);

            if (metadata.isPresent()) {
                updated.add(updatedOfficial);
            } else {
                inserted.add(updatedOfficial);
            }
        }

        return SyncResult.refreshed(scanned, inserted, updated, timestamp);
    }

    private String computeVersionHash(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to compute SHA-1 hash", ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public record SyncResult(
            boolean refreshed,
            boolean skipped,
            boolean lockHeldByOther,
            int scanned,
            List<PublicOfficial> insertedOfficials,
            List<PublicOfficial> updatedOfficials,
            Instant refreshedAt) {

        private static final SyncResult LOCKED = new SyncResult(false, false, true, 0, List.of(), List.of(), null);

        public static SyncResult skipped(Instant lastRefreshedAt) {
            return new SyncResult(false, true, false, 0, List.of(), List.of(), lastRefreshedAt);
        }

        public static SyncResult locked() {
            return LOCKED;
        }

        public static SyncResult refreshed(int scanned, List<PublicOfficial> inserted, List<PublicOfficial> updated, Instant refreshedAt) {
            return new SyncResult(true, false, false, scanned, List.copyOf(inserted), List.copyOf(updated), refreshedAt);
        }
    }
}
