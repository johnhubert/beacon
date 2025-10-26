package com.beacon.ingest.usafed.service;

import com.beacon.common.accountability.v1.AccountabilityMetric;
import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.JurisdictionType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.MemberVote;
import com.beacon.common.accountability.v1.OfficialAccountabilityEvent;
import com.beacon.common.accountability.v1.OfficeStatus;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.common.accountability.v1.VotePosition;
import com.beacon.common.accountability.v1.VotingRecord;
import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.publisher.AccountabilityEventPublisher;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FederalIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederalIngestionService.class);

    private final AccountabilityEventPublisher publisher;
    private final CongressApiProperties properties;

    public FederalIngestionService(AccountabilityEventPublisher publisher, CongressApiProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    public void ingestLatestSnapshots() {
        // TODO: Replace placeholder with Congress.gov delta polling + LegiScan integrations per ingestion_plan.md.
        LOGGER.info("Polling {} for latest federal activity (placeholder)", properties.baseUrl());
        OfficialAccountabilityEvent placeholderEvent = buildPlaceholderEvent();
        publisher.publish(placeholderEvent);
    }

    private OfficialAccountabilityEvent buildPlaceholderEvent() {
        Instant now = Instant.now();
        String legislativeBodyUuid = UUID.randomUUID().toString();
        String publicOfficialUuid = UUID.randomUUID().toString();
        String votingRecordUuid = UUID.randomUUID().toString();

        LegislativeBody legislativeBody = LegislativeBody.newBuilder()
                .setUuid(legislativeBodyUuid)
                .setSourceId("US-SENATE-118")
                .setJurisdictionType(JurisdictionType.FEDERAL)
                .setJurisdictionCode("US")
                .setName("U.S. Senate")
                .setChamberType(ChamberType.UPPER)
                .setSession("118")
                .build();

        PublicOfficial publicOfficial = PublicOfficial.newBuilder()
                .setUuid(publicOfficialUuid)
                .setSourceId("S000033")
                .setLegislativeBodyUuid(legislativeBodyUuid)
                .setFullName("Placeholder Senator")
                .setPartyAffiliation("I")
                .setRoleTitle("Senator")
                .setJurisdictionRegionCode("ME")
                .setDistrictIdentifier("At-large")
                .setTermStartDate(toTimestamp(now.minus(Duration.ofDays(730))))
                .setOfficeStatus(OfficeStatus.ACTIVE)
                .setPhotoUrl("https://placehold.co/128x128")
                .build();

        MemberVote memberVote = MemberVote.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setSourceId("S000033-RC_118_12")
                .setOfficialUuid(publicOfficialUuid)
                .setVotingRecordUuid(votingRecordUuid)
                .setVotePosition(VotePosition.YEA)
                .setGroupPosition("Independent")
                .build();

        VotingRecord votingRecord = VotingRecord.newBuilder()
                .setUuid(votingRecordUuid)
                .setSourceId("118-2-12")
                .setLegislativeBodyUuid(legislativeBodyUuid)
                .setVoteDateUtc(toTimestamp(now.minusSeconds(3600)))
                .setSubjectSummary("Concurrent resolution placeholder")
                .setBillReference("S.CON.RES.5")
                .setBillUri("https://www.congress.gov/concurrent-resolutions")
                .setRollCallReference("RC_118_12")
                .addMemberVotes(memberVote)
                .build();

        AccountabilityMetric accountabilityMetric = AccountabilityMetric.newBuilder()
                .setUuid("alignment-placeholder")
                .setSourceId("alignment-v1")
                .setName("Statement vs Action Alignment")
                .setScore(0.82)
                .setMethodologyVersion("v1-alpha")
                .setDetails("Placeholder metric until scoring service is built")
                .build();

        return OfficialAccountabilityEvent.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setSourceId("congress.gov-placeholder")
                .setCapturedAt(toTimestamp(now))
                .setIngestionSource("congress.gov")
                .setPartitionKey(buildPartitionKey(publicOfficialUuid))
                .setLegislativeBody(legislativeBody)
                .setPublicOfficial(publicOfficial)
                .addVotingRecords(votingRecord)
                .addAccountabilityMetrics(accountabilityMetric)
                .build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private String buildPartitionKey(String officialUuid) {
        return "%s::%s".formatted(officialUuid, properties.chamber().toLowerCase());
    }
}
