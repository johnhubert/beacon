package com.beacon.ingest.usafed.service;

import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.OfficialAccountabilityEvent;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientException;
import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.publisher.AccountabilityEventPublisher;
import com.beacon.stateful.mongo.sync.RosterEntry;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService.SyncResult;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    private final AccountabilityEventPublisher publisher;
    private final CongressApiProperties properties;
    private final CongressGovClient congressGovClient;
    private final RosterSynchronizationService rosterSynchronizationService;

    public FederalIngestionService(
            AccountabilityEventPublisher publisher,
            CongressApiProperties properties,
            CongressGovClient congressGovClient,
            RosterSynchronizationService rosterSynchronizationService) {
        this.publisher = publisher;
        this.properties = properties;
        this.congressGovClient = congressGovClient;
        this.rosterSynchronizationService = rosterSynchronizationService;
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
