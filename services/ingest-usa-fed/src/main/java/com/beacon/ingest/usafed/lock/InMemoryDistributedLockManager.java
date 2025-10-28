package com.beacon.ingest.usafed.lock;

import com.beacon.stateful.mongo.lock.DistributedLockManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory lock manager used for tests and local environments where Redis is unavailable.
 */
public class InMemoryDistributedLockManager implements DistributedLockManager {

    private static final class LockRecord {
        final String token;
        final Instant expiresAt;

        LockRecord(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }

    private final Map<String, LockRecord> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, String token, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(ttl, "ttl");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        return locks.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new LockRecord(token, expiresAt);
            }
            return existing;
        }).token.equals(token);
    }

    @Override
    public void release(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        locks.computeIfPresent(key, (k, existing) -> {
            if (token.equals(existing.token)) {
                return null;
            }
            return existing;
        });
    }
}
