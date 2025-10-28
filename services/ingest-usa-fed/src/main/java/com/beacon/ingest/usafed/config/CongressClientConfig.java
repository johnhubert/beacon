package com.beacon.ingest.usafed.config;

import com.beacon.congress.client.CongressGovClient;
import com.beacon.congress.client.CongressGovClientConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CongressClientConfig {

    @Bean
    public CongressGovClient congressGovClient(CongressApiProperties properties) {
        CongressGovClientConfig config = CongressGovClientConfig.builder()
                .baseUrl(properties.baseUrl() == null ? null : properties.baseUrl().toString())
                .apiKey(properties.apiKey())
                .build();
        return new CongressGovClient(config);
    }
}
