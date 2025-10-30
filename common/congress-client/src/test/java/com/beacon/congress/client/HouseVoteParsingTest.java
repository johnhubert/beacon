package com.beacon.congress.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HouseVoteParsingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesMemberVotesFromNestedResults() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/house-vote-sample.json")) {
            JsonNode root = MAPPER.readTree(stream);
            JsonNode container = root.path("houseRollCallVoteMemberVotes");
            Map<String, CongressGovClient.MemberVoteResult> results = new LinkedHashMap<>();

            CongressGovClient.collectMemberVotes(container, results);

            assertThat(results).hasSize(3);
            assertThat(results.get("A000055").voteCast()).isEqualTo("Yea");
            assertThat(results.get("A000370").voteCast()).isEqualTo("Nay");
        }
    }
}
