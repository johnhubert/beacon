package com.beacon.stateful.mongo.lock;

import java.time.Duration;

/**
 * Minimal abstraction for acquiring and releasing distributed locks with automatic expiration.
 */
public interface DistributedLockManager {

    /**
     * Attempts to acquire a lock with the provided key and token for the given TTL.
     *
     * @param key the lock identifier
     * @param token unique token representing the caller
     * @param ttl duration after which the lock should automatically expire
     * @return {@code true} when the lock was acquired, {@code false} otherwise
     */
    boolean tryAcquire(String key, String token, Duration ttl);

    /**
     * Releases the lock when the provided token still matches the current lock owner.
     *
     * @param key the lock identifier
     * @param token token originally used when acquiring the lock
     */
    void release(String key, String token);
}
