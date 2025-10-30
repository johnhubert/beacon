package com.beacon.ingest.usafed.lock;

import com.beacon.stateful.mongo.lock.DistributedLockManager;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis-backed {@link DistributedLockManager} that relies on key expiry to prevent deadlocks.
 */
public class RedisDistributedLockManager implements DistributedLockManager {

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end";

    private static final DefaultRedisScript<Long> RELEASE_REDIS_SCRIPT = new DefaultRedisScript<>(
            RELEASE_SCRIPT,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public boolean tryAcquire(String key, String token, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String key, String token) {
        redisTemplate.execute(
                RELEASE_REDIS_SCRIPT,
                java.util.Collections.singletonList(key),
                token);
    }
}
