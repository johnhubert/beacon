package com.beacon.stateful.mongo.sync;

import com.beacon.common.accountability.v1.PublicOfficial;

/**
 * Represents a single roster entry fetched from an upstream data source.
 */
public record RosterEntry(PublicOfficial official, String sourcePayload) {
    public RosterEntry {
        if (official == null) {
            throw new IllegalArgumentException("official must not be null");
        }
    }
}
