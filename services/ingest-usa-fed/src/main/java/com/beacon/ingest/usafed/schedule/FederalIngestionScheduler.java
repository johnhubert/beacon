package com.beacon.ingest.usafed.schedule;

import com.beacon.ingest.usafed.config.CongressApiProperties;
import com.beacon.ingest.usafed.service.FederalIngestionService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "beacon.congress.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class FederalIngestionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederalIngestionScheduler.class);

    private final FederalIngestionService ingestionService;
    private final CongressApiProperties properties;
    private final TaskScheduler taskScheduler;
    private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

    public FederalIngestionScheduler(
            FederalIngestionService ingestionService,
            CongressApiProperties properties,
            TaskScheduler taskScheduler) {
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Kickstarts the refresh loop once the Spring application is ready. Runs a refresh immediately and
     * schedules the follow-up run so we don't rely solely on fixed delays from the framework.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void triggerOnStartup() {
        LOGGER.info("Starting initial congressional roster refresh");
        refreshAndScheduleNext();
    }

    private void refreshAndScheduleNext() {
        // Run the refresh and always schedule the next execution, even if the current run fails.
        try {
            ingestionService.refreshCongressRoster();
        } finally {
            scheduleNextRun();
        }
    }

    /**
     * Computes the next execution time relative to the moment this invocation finishes and replaces any
     * previously scheduled task so the cadence never drifts or overlaps.
     */
    private void scheduleNextRun() {
        Duration interval = properties.pollInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            LOGGER.warn("Skipping scheduler setup because poll interval is not configured.");
            return;
        }
        Instant nextRunAt = Instant.now().plus(interval);
        ScheduledFuture<?> nextFuture = taskScheduler.schedule(this::refreshAndScheduleNext, nextRunAt);
        ScheduledFuture<?> previous = scheduledFuture.getAndSet(nextFuture);
        if (previous != null) {
            previous.cancel(false);
        }
    }
}
