package com.beacon.tests.congress;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.common.accountability.v1.ChamberType;
import com.beacon.common.accountability.v1.LegislativeBody;
import com.beacon.common.accountability.v1.PublicOfficial;
import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientConfig;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CongressGovIntegrationTest {

    private CongressGovClient client;
    private int congressNumber;
    private List<LegislativeBody> legislativeBodies;
    private List<PublicOfficial> houseMembers;
    private List<PublicOfficial> senateMembers;

    @BeforeAll
    void setupClient() {
        String apiKey = System.getProperty("CONGRESS_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "CONGRESS_API_KEY must be provided to run integration tests");
        this.congressNumber = Integer.parseInt(System.getProperty("CONGRESS_NUMBER", "118"));
        CongressGovClientConfig config = CongressGovClientConfig.builder()
                .apiKey(apiKey)
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        this.client = new CongressGovClient(config);
        this.legislativeBodies = client.fetchLegislativeBodies(congressNumber);
        this.houseMembers = client.fetchMembers(congressNumber, ChamberType.LOWER);
        this.senateMembers = client.fetchMembers(congressNumber, ChamberType.UPPER);
    }

    @Test
    @DisplayName("Legislative bodies are discoverable with source identifiers")
    void legislativeBodiesContainExpectedFields() {
        assertThat(legislativeBodies)
                .as("House and Senate entries should be returned")
                .hasSizeGreaterThanOrEqualTo(2);

        Optional<LegislativeBody> house = legislativeBodies.stream()
                .filter(body -> body.getChamberType() == ChamberType.LOWER)
                .findFirst();
        Optional<LegislativeBody> senate = legislativeBodies.stream()
                .filter(body -> body.getChamberType() == ChamberType.UPPER)
                .findFirst();

        assertThat(house)
                .isPresent()
                .get()
                .satisfies(body -> {
                    assertThat(body.getSourceId()).isNotBlank();
                    assertThat(body.getUuid()).isNotBlank();
                    assertThat(body.getJurisdictionCode()).isEqualTo("US");
                });

        assertThat(senate)
                .isPresent()
                .get()
                .satisfies(body -> {
                    assertThat(body.getSourceId()).isNotBlank();
                    assertThat(body.getUuid()).isNotBlank();
                    assertThat(body.getJurisdictionCode()).isEqualTo("US");
                });
    }

    @Test
    @DisplayName("House roster returns populated PublicOfficial protobufs")
    void houseMembersAreMapped() {
        assertThat(houseMembers)
                .as("Expected current House membership")
                .isNotEmpty();

        PublicOfficial sample = houseMembers.get(0);
        assertThat(sample.getSourceId()).isNotBlank();
        assertThat(sample.getLegislativeBodyUuid()).isNotBlank();
        assertThat(sample.getFullName()).isNotBlank();
        assertThat(sample.getPartyAffiliation()).isNotBlank();
        assertThat(sample.getJurisdictionRegionCode()).hasSize(2);
    }

    @Test
    @DisplayName("Senate roster includes full membership")
    void senateRosterIncludesFullMembership() {
        assertThat(senateMembers)
                .as("Expect seated senators to be discoverable")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Detailed member lookups remain compatible with protobuf mapping")
    void memberDetailsRoundTrip() {
        PublicOfficial target = houseMembers.get(ThreadLocalRandom.current().nextInt(houseMembers.size()));
        Optional<PublicOfficial> detailed = client.fetchMemberDetails(target.getSourceId(), congressNumber);

        assertThat(detailed)
                .isPresent()
                .get()
                .satisfies(member -> {
                    assertThat(member.getSourceId()).isEqualTo(target.getSourceId());
                    assertThat(member.getFullName()).isNotBlank();
                    assertThat(member.getLegislativeBodyUuid()).isEqualTo(target.getLegislativeBodyUuid());
                });
    }
}
