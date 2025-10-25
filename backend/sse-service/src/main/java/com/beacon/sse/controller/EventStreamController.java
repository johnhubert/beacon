package com.beacon.sse.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class EventStreamController {

    private static final Logger log = LoggerFactory.getLogger(EventStreamController.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong counter = new AtomicLong();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                long eventId = counter.incrementAndGet();
                emitter.send(SseEmitter.event()
                        .id(Long.toString(eventId))
                        .data(Map.of(
                                "message", "Live beacon event " + eventId,
                                "publishedAt", Instant.now().toString())));
            } catch (IOException e) {
                log.warn("Unable to push SSE event: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> {
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
