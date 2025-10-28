package com.beacon.ingest.usafed.service;

import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.OfficialAccountabilityEvent;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClient.MemberListing;
import com.beacon.congress.client.CongressGovClientException;
import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.publisher.AccountabilityEventPublisher;
import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository.OfficialMetadata;
import com.google.protobuf.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FederalIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederalIngestionService.class);
    private static final String ROSTER_REFRESH_LOCK_KEY = "ingest-usa-fed:congress:roster-refresh";
    private static final Duration ROSTER_LOCK_TTL = Duration.ofMinutes(55);
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String INGESTION_SOURCE = "congress.gov";

    private final AccountabilityEventPublisher publisher;
    private final CongressApiProperties properties;
    private final PublicOfficialRepository publicOfficialRepository;
    private final LegislativeBodyRepository legislativeBodyRepository;
    private final CongressGovClient congressGovClient;
    private final StringRedisTemplate redisTemplate;

    public FederalIngestionService(
            AccountabilityEventPublisher publisher,
            CongressApiProperties properties,
            CongressGovClient congressGovClient,
            StringRedisTemplate redisTemplate,
            Optional<PublicOfficialRepository> publicOfficialRepository,
            Optional<LegislativeBodyRepository> legislativeBodyRepository) {
        this.publisher = publisher;
        this.properties = properties;
        this.congressGovClient = congressGovClient;
        this.redisTemplate = redisTemplate;
        this.publicOfficialRepository = publicOfficialRepository.orElse(null);
        this.legislativeBodyRepository = legislativeBodyRepository.orElse(null);
    }

    public void refreshCongressRoster() {
        if (publicOfficialRepository == null || legislativeBodyRepository == null) {
            LOGGER.warn("Mongo repositories unavailable; skipping roster refresh");
            return;
        }
        if (redisTemplate == null) {
            LOGGER.warn("Redis template unavailable; skipping roster refresh");
            return;
        }
        String lockValue = UUID.randomUUID().toString();
        if (!acquireLock(lockValue)) {
            LOGGER.debug("Another instance is already refreshing the congressional roster; skipping run");
            return;
        }

        Instant start = Instant.now();
        LOGGER.info("Refreshing congressional roster for {}th Congress", properties.congressNumber());
        int totalProcessed = 0;
        int totalInserts = 0;
        int totalUpdates = 0;

        try {
            List<LegislativeBody> bodies = congressGovClient.fetchLegislativeBodies(properties.congressNumber());
            for (LegislativeBody body : bodies) {
                legislativeBodyRepository.upsert(body);
                List<MemberListing> listings = congressGovClient.fetchMemberListings(properties.congressNumber(), body.getChamberType());
                RefreshStats stats = synchronizeBody(body, listings);
                totalProcessed += stats.scanned();
                totalInserts += stats.inserted();
                totalUpdates += stats.updated();
                long storedCount = publicOfficialRepository.countByLegislativeBody(body.getUuid());
                LOGGER.info(
                        "Refreshed {} members for {} ({} inserts, {} updates, {} total stored)",
                        stats.scanned(),
                        body.getName(),
                        stats.inserted(),
                        stats.updated(),
                        storedCount);
            }
            Duration elapsed = Duration.between(start, Instant.now());
            LOGGER.info(
                    "Roster refresh complete: {} members processed, {} inserts, {} updates in {} ms",
                    totalProcessed,
                    totalInserts,
                    totalUpdates,
                    elapsed.toMillis());
        } catch (CongressGovClientException ex) {
            LOGGER.warn(
                    "Congress.gov roster refresh skipped: {}. Verify CONGRESS_API_KEY or network access before retrying.",
                    ex.getMessage());
        } catch (Exception ex) {
            LOGGER.error("Failed to refresh congressional roster", ex);
        } finally {
            releaseLock(lockValue);
        }
    }

    private RefreshStats synchronizeBody(LegislativeBody body, List<MemberListing> listings) {
        int scanned = 0;
        int inserts = 0;
        int updates = 0;

        for (MemberListing listing : listings) {
            scanned++;
            PublicOfficial official = listing.publicOfficial();
            String versionHash = computeVersionHash(listing.sourceJson());
            Optional<OfficialMetadata> existingMetadata = publicOfficialRepository.findMetadataBySourceId(official.getSourceId());
            if (existingMetadata.isPresent() && Objects.equals(existingMetadata.get().versionHash(), versionHash)) {
                continue;
            }

            PublicOfficial.Builder builder = official.toBuilder()
                    .setVersionHash(versionHash)
                    .clearUuid();

            existingMetadata
                    .map(OfficialMetadata::uuid)
                    .filter(uuid -> uuid != null && !uuid.isBlank())
                    .ifPresentOrElse(
                            builder::setUuid,
                            () -> builder.setUuid(official.getUuid()));

            if (existingMetadata.isPresent()
                    && existingMetadata.get().uuid() != null
                    && !existingMetadata.get().uuid().equals(official.getUuid())) {
                LOGGER.debug(
                        "Preserving persisted UUID {} for official {} (computed uuid was {})",
                        existingMetadata.get().uuid(),
                        official.getSourceId(),
                        official.getUuid());
            }

            PublicOfficial updated = builder.build();
            publicOfficialRepository.upsertOfficial(updated);
            publishRosterEvent(body, updated, existingMetadata.isPresent());
            if (existingMetadata.isPresent()) {
                updates++;
            } else {
                inserts++;
            }
        }

        return new RefreshStats(scanned, inserts, updates);
    }

    private boolean acquireLock(String lockValue) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(ROSTER_REFRESH_LOCK_KEY, lockValue, ROSTER_LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            LOGGER.warn("Unable to acquire roster refresh lock, skipping run", ex);
            return false;
        }
    }

    private void releaseLock(String lockValue) {
        try {
            String current = redisTemplate.opsForValue().get(ROSTER_REFRESH_LOCK_KEY);
            if (lockValue.equals(current)) {
                redisTemplate.delete(ROSTER_REFRESH_LOCK_KEY);
            }
        } catch (Exception ex) {
            LOGGER.warn("Unable to release roster refresh lock", ex);
        }
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

    private String buildPartitionKey(String officialUuid, LegislativeBody body) {
        String chamber = body.getChamberType().name().toLowerCase(Locale.ROOT);
        return "%s::%s".formatted(officialUuid, chamber);
    }

    private record RefreshStats(int scanned, int inserted, int updated) {}
}
