package com.beacon.ingest.usafed;

import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.VotingRecordRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class TestBeansConfiguration {

    @Bean
    PublicOfficialRepository publicOfficialRepository() {
        return Mockito.mock(PublicOfficialRepository.class);
    }

    @Bean
    LegislativeBodyRepository legislativeBodyRepository() {
        return Mockito.mock(LegislativeBodyRepository.class);
    }

    @Bean
    VotingRecordRepository votingRecordRepository() {
        return Mockito.mock(VotingRecordRepository.class);
    }
}
