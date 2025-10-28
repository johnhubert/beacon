package com.beacon.ingest.usafed.schedule;

import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.service.FederalIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    @EventListener(ApplicationReadyEvent.class)
    public void triggerOnStartup() {
        LOGGER.info("Starting initial congressional roster refresh");
        ingestionService.refreshCongressRoster();
    }

    @Scheduled(
            fixedDelayString = "${beacon.congress.poll-interval:PT1H}",
            initialDelayString = "${beacon.congress.poll-interval:PT1H}")
    public void refreshCongressRoster() {
        LOGGER.debug("Triggering scheduled roster refresh for {}", properties.baseUrl());
        ingestionService.refreshCongressRoster();
    }
}
