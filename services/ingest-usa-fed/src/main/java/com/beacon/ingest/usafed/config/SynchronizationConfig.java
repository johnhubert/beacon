package com.beacon.ingest.usafed.config;

import com.beacon.ingest.usafed.lock.InMemoryDistributedLockManager;
import com.beacon.ingest.usafed.lock.RedisDistributedLockManager;
import com.beacon.stateful.mongo.LegislativeBodyRepository;
import com.beacon.stateful.mongo.PublicOfficialRepository;
import com.beacon.stateful.mongo.lock.DistributedLockManager;
import com.beacon.stateful.mongo.sync.RosterSynchronizationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class SynchronizationConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public DistributedLockManager distributedLockManager(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLockManager(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockManager.class)
    public DistributedLockManager inMemoryDistributedLockManager() {
        return new InMemoryDistributedLockManager();
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
