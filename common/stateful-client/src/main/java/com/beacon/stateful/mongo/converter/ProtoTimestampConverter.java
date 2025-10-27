package com.beacon.stateful.mongo.converter;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

final class ProtoTimestampConverter {

    private ProtoTimestampConverter() {}

    static Optional<Date> toDate(Timestamp timestamp) {
        if (timestamp == null || timestamp.getSeconds() == 0 && timestamp.getNanos() == 0) {
            return Optional.empty();
        }
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return Optional.of(Date.from(instant));
    }

    static Optional<Timestamp> toTimestamp(Date date) {
        return Optional.ofNullable(date)
                .map(Date::toInstant)
                .map(instant -> Timestamp.newBuilder()
                        .setSeconds(instant.getEpochSecond())
                        .setNanos(instant.getNano())
                        .build());
    }
}
