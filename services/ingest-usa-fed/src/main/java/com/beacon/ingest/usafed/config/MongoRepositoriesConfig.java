package com.beacon.ingest.usafed.config;

import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.MongoStatefulClient;
import com.beacon.stateful.mongo.MongoStatefulConfig;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.VotingRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoRepositoriesConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "stateful.mongo.enabled", matchIfMissing = true)
    public MongoStatefulClient mongoStatefulClient() {
        return new MongoStatefulClient(MongoStatefulConfig.createDefault());
    }

    @Bean
    @ConditionalOnProperty(name = "stateful.mongo.enabled", matchIfMissing = true)
    public PublicOfficialRepository publicOfficialRepository(MongoStatefulClient client) {
        return client.publicOfficials();
    }

    @Bean
    @ConditionalOnProperty(name = "stateful.mongo.enabled", matchIfMissing = true)
    public LegislativeBodyRepository legislativeBodyRepository(MongoStatefulClient client) {
        return client.legislativeBodies();
    }

    @Bean
    @ConditionalOnProperty(name = "stateful.mongo.enabled", matchIfMissing = true)
    public VotingRecordRepository votingRecordRepository(MongoStatefulClient client) {
        return client.votingRecords();
    }
}
