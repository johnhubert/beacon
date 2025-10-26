package com.beacon.ingest.usafed.publisher;

import com.beacon.common.accountability.v1.OfficialAccountabilityEvent;
import com.beacon.common.topics.KafkaTopic;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class AccountabilityEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountabilityEventPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public AccountabilityEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OfficialAccountabilityEvent event) {
        byte[] payload = event.toByteArray();
        String partitionKey = event.getPartitionKey();
        CompletableFuture<SendResult<String, byte[]>> future = kafkaTemplate.send(
                KafkaTopic.OFFICIAL_ACCOUNTABILITY_EVENTS.value(), partitionKey, payload);

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Failed to publish accountability event {}", event.getUuid(), throwable);
                return;
            }

            RecordMetadata metadata = result.getRecordMetadata();
            LOGGER.info(
                    "Published accountability event {} to topic {} partition {} offset {}",
                    event.getUuid(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset());
        });
    }
}
