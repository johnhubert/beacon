package com.beacon.ingest.usafed.schedule;

import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.service.FederalIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FederalIngestionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederalIngestionScheduler.class);

    private final FederalIngestionService ingestionService;
    private final CongressApiProperties properties;

    public FederalIngestionScheduler(FederalIngestionService ingestionService, CongressApiProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${beacon.congress.poll-interval:PT5M}")
    public void pollCongressFeeds() {
        LOGGER.debug("Triggering ingest cycle for {}", properties.baseUrl());
        ingestionService.ingestLatestSnapshots();
    }
}
