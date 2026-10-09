package com.example.urlshortener.cache;

import com.example.urlshortener.dto.CachedUrlRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);
    private static final String KEY_PREFIX = "url:";

    private final RedisTemplate<String, CachedUrlRecord> redisTemplate;
    private final boolean cacheEnabled;
    private final long defaultTtlSeconds;

    public UrlCacheService(
            RedisTemplate<String, CachedUrlRecord> redisTemplate,
            @Value("${app.cache.enabled:true}") boolean cacheEnabled,
            @Value("${app.cache.ttl-seconds:1800}") long defaultTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.cacheEnabled = cacheEnabled;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Looks up cached URL record by short code.
     * Gracefully degrades to database query on Redis failure.
     */
    public Optional<CachedUrlRecord> get(String shortCode) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String key = formatKey(shortCode);
            CachedUrlRecord cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache HIT for shortCode: {}", shortCode);
                return Optional.of(cached);
            }
            log.debug("Cache MISS for shortCode: {}", shortCode);
        } catch (Exception ex) {
            log.warn("Redis lookup failed for key '{}'. Falling back to database: {}",
                    formatKey(shortCode), ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Caches URL record with bounded TTL.
     * If URL has an expiry timestamp, TTL will not outlive that expiry.
     */
    public void put(CachedUrlRecord record) {
        if (!cacheEnabled || record == null || record.shortCode() == null) {
            return;
        }

        try {
            long effectiveTtl = defaultTtlSeconds;

            if (record.expiresAt() != null) {
                long secondsUntilExpiry = Duration.between(Instant.now(), record.expiresAt()).getSeconds();
                if (secondsUntilExpiry <= 0) {
                    // Record is already expired, do not cache
                    return;
                }
                effectiveTtl = Math.min(defaultTtlSeconds, secondsUntilExpiry);
            }

            String key = formatKey(record.shortCode());
            redisTemplate.opsForValue().set(key, record, effectiveTtl, TimeUnit.SECONDS);
            log.debug("Cached shortCode: {} with TTL: {}s", record.shortCode(), effectiveTtl);
        } catch (Exception ex) {
            log.warn("Failed to cache URL record for key '{}': {}",
                    formatKey(record.shortCode()), ex.getMessage());
        }
    }

    /**
     * Evicts cached mapping on update, deactivation or deletion.
     */
    public void evict(String shortCode) {
        if (!cacheEnabled || shortCode == null) {
            return;
        }

        try {
            String key = formatKey(shortCode);
            redisTemplate.delete(key);
            log.debug("Evicted cache for shortCode: {}", shortCode);
        } catch (Exception ex) {
            log.warn("Failed to evict cache key '{}': {}", formatKey(shortCode), ex.getMessage());
        }
    }

    private String formatKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
