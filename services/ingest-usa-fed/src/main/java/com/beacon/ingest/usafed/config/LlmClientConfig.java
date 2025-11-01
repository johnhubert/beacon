package com.beacon.ingest.usafed.config;

import com.beacon.ingest.usafed.llm.LlmClient;
import com.beacon.ingest.usafed.llm.OpenAiLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfig {

    @Bean
    public LlmClient openAiLlmClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
        return new OpenAiLlmClient(httpClient, objectMapper, properties);
    }
}
