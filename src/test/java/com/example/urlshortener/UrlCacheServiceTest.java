package com.example.urlshortener;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CachedUrlRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UrlCacheServiceTest {

    private RedisTemplate<String, CachedUrlRecord> redisTemplate;
    private ValueOperations<String, CachedUrlRecord> valueOps;
    private UrlCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cacheService = new UrlCacheService(redisTemplate, true, 1800);
    }

    @Test
    @DisplayName("Returns cached record when present in Redis")
    void shouldReturnCachedEntryOnHit() {
        CachedUrlRecord cached = new CachedUrlRecord(1L, "abc", "https://example.com", null, true);
        when(valueOps.get("url:abc")).thenReturn(cached);

        Optional<CachedUrlRecord> result = cacheService.get("abc");

        assertThat(result).isPresent().contains(cached);
    }

    @Test
    @DisplayName("Returns empty optional on cache miss")
    void shouldReturnEmptyOnMiss() {
        when(valueOps.get("url:missing")).thenReturn(null);

        Optional<CachedUrlRecord> result = cacheService.get("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Gracefully falls back to empty optional when Redis throws connection error")
    void shouldFallbackGracefullyOnRedisOutage() {
        when(valueOps.get("url:err")).thenThrow(new RedisConnectionFailureException("Connection refused"));

        Optional<CachedUrlRecord> result = cacheService.get("err");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Calculates bounded TTL not exceeding URL expiry time")
    void shouldSetBoundedTtlForExpiringUrl() {
        Instant futureExpiry = Instant.now().plusSeconds(600); // 10 minutes
        CachedUrlRecord record = new CachedUrlRecord(2L, "exp123", "https://exp.com", futureExpiry, true);

        cacheService.put(record);

        // TTL should be ~600 seconds instead of default 1800s
        verify(valueOps, times(1)).set(eq("url:exp123"), eq(record), longThat(ttl -> ttl <= 600 && ttl >= 595), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Does not cache already expired URLs")
    void shouldNotCacheAlreadyExpiredUrl() {
        Instant pastExpiry = Instant.now().minusSeconds(10);
        CachedUrlRecord record = new CachedUrlRecord(3L, "expired", "https://exp.com", pastExpiry, true);

        cacheService.put(record);

        verify(valueOps, never()).set(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("Evicts cache entry when requested")
    void shouldEvictCacheEntry() {
        cacheService.evict("deleteMe");

        verify(redisTemplate, times(1)).delete("url:deleteMe");
    }

    @Test
    @DisplayName("Bypasses Redis operations completely when cache is disabled")
    void shouldBypassWhenDisabled() {
        UrlCacheService disabledCache = new UrlCacheService(redisTemplate, false, 1800);

        assertThat(disabledCache.get("any")).isEmpty();
        disabledCache.put(new CachedUrlRecord(1L, "any", "https://a.com", null, true));
        disabledCache.evict("any");

        verifyNoInteractions(redisTemplate);
    }
}
