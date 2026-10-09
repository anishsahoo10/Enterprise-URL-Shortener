package com.example.urlshortener;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CachedUrlRecord;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.UrlExpiredOrInactiveException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.service.AnalyticsService;
import com.example.urlshortener.service.RedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedirectServiceTest {

    private UrlRepository urlRepository;
    private UrlCacheService urlCacheService;
    private AnalyticsService analyticsService;
    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        urlCacheService = mock(UrlCacheService.class);
        analyticsService = mock(AnalyticsService.class);
        redirectService = new RedirectService(urlRepository, urlCacheService, analyticsService);
    }

    @Test
    @DisplayName("Cache hit returns destination and records click without database query")
    void shouldRedirectFromCacheOnHit() {
        CachedUrlRecord cached = new CachedUrlRecord(1L, "code123", "https://cached-destination.com", null, true);
        when(urlCacheService.get("code123")).thenReturn(Optional.of(cached));

        String destination = redirectService.resolveAndTrack("code123", "https://ref.com", "Mozilla/5.0");

        assertThat(destination).isEqualTo("https://cached-destination.com");
        verifyNoInteractions(urlRepository);
        verify(analyticsService, times(1)).recordClick(1L, "https://ref.com", "Mozilla/5.0");
    }

    @Test
    @DisplayName("Cache miss queries database, populates cache, and redirects")
    void shouldQueryDatabaseAndPopulateCacheOnMiss() {
        when(urlCacheService.get("code456")).thenReturn(Optional.empty());

        Url dbUrl = new Url("code456", "https://db-destination.com", Instant.now(), null);
        dbUrl.setId(5L);
        when(urlRepository.findByShortCode("code456")).thenReturn(Optional.of(dbUrl));

        String destination = redirectService.resolveAndTrack("code456", null, null);

        assertThat(destination).isEqualTo("https://db-destination.com");
        verify(urlRepository, times(1)).findByShortCode("code456");
        verify(urlCacheService, times(1)).put(any(CachedUrlRecord.class));
        verify(analyticsService, times(1)).recordClick(5L, null, null);
    }

    @Test
    @DisplayName("Throws UrlExpiredOrInactiveException when link is inactive in cache")
    void shouldRejectInactiveCachedLink() {
        CachedUrlRecord cached = new CachedUrlRecord(1L, "inactive1", "https://dest.com", null, false);
        when(urlCacheService.get("inactive1")).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> redirectService.resolveAndTrack("inactive1", null, null))
                .isInstanceOf(UrlExpiredOrInactiveException.class);

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Throws UrlExpiredOrInactiveException when link is expired in database")
    void shouldRejectExpiredDatabaseLink() {
        when(urlCacheService.get("expired1")).thenReturn(Optional.empty());

        Url expiredUrl = new Url("expired1", "https://dest.com", Instant.now().minusSeconds(100), Instant.now().minusSeconds(10));
        expiredUrl.setId(9L);
        when(urlRepository.findByShortCode("expired1")).thenReturn(Optional.of(expiredUrl));

        assertThatThrownBy(() -> redirectService.resolveAndTrack("expired1", null, null))
                .isInstanceOf(UrlExpiredOrInactiveException.class);

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Throws UrlNotFoundException when code does not exist in cache or database")
    void shouldThrowNotFoundForUnknownCode() {
        when(urlCacheService.get("unknown")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.resolveAndTrack("unknown", null, null))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    @DisplayName("Redirect proceeds successfully even if analytics recording fails")
    void shouldProceedWhenAnalyticsFails() {
        CachedUrlRecord cached = new CachedUrlRecord(1L, "codeFail", "https://dest.com", null, true);
        when(urlCacheService.get("codeFail")).thenReturn(Optional.of(cached));
        doThrow(new RuntimeException("DB click write failed"))
                .when(analyticsService).recordClick(any(), any(), any());

        String destination = redirectService.resolveAndTrack("codeFail", null, null);

        assertThat(destination).isEqualTo("https://dest.com");
    }
}
