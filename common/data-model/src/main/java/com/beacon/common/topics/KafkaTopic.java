package com.beacon.common.topics;

/**
 * Canonical Kafka topic definitions shared across Beacon microservices.
 */
public enum KafkaTopic {
    OFFICIAL_ACCOUNTABILITY_EVENTS("official-accountability-events", 6);

    private final String value;
    private final int partitions;

    KafkaTopic(String value, int partitions) {
        this.value = value;
        this.partitions = partitions;
    }

    public String value() {
        return value;
    }

    public int partitions() {
        return partitions;
    }
}
