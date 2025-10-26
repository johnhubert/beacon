package com.beacon.ingest.usafed;

import com.beacon.ingest.usafed.config.CongressApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CongressApiProperties.class)
public class IngestUsaFedApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestUsaFedApplication.class, args);
    }
}
