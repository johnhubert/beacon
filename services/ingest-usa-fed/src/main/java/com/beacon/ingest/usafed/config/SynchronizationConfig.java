package com.beacon.ingest.usafed.config;

import com.beacon.ingest.usafed.lock.RedisDistributedLockManager;
import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.lock.DistributedLockManager;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SynchronizationConfig {

    @Bean
    public DistributedLockManager distributedLockManager(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLockManager(redisTemplate);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RosterSynchronizationService rosterSynchronizationService(
            PublicOfficialRepository publicOfficialRepository,
            LegislativeBodyRepository legislativeBodyRepository,
            DistributedLockManager distributedLockManager,
            Clock systemClock) {
        return new RosterSynchronizationService(publicOfficialRepository, legislativeBodyRepository, distributedLockManager, systemClock);
    }
}
