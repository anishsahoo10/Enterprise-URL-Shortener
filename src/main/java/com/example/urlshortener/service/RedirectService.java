package com.example.urlshortener.service;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CachedUrlRecord;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.UrlExpiredOrInactiveException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RedirectService {

    private static final Logger log = LoggerFactory.getLogger(RedirectService.class);

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final AnalyticsService analyticsService;

    public RedirectService(
            UrlRepository urlRepository,
            UrlCacheService urlCacheService,
            AnalyticsService analyticsService) {
        this.urlRepository = urlRepository;
        this.urlCacheService = urlCacheService;
        this.analyticsService = analyticsService;
    }

    /**
     * Resolves shortCode to destination URL using cache-aside pattern.
     * Enforces active and expiry lifecycle policies on both cache hit and miss.
     * Records click analytics asynchronously/isolated from redirect failures.
     */
    public String resolveAndTrack(String shortCode, String referrer, String userAgent) {
        Long targetId;
        String destinationUrl;

        // 1. Check Redis cache
        Optional<CachedUrlRecord> cachedOpt = urlCacheService.get(shortCode);

        if (cachedOpt.isPresent()) {
            CachedUrlRecord cached = cachedOpt.get();
            // Enforce lifecycle rules on cached entry
            if (!cached.active() || cached.isExpired()) {
                log.info("Cached shortCode '{}' is inactive or expired", shortCode);
                throw new UrlExpiredOrInactiveException(shortCode);
            }
            targetId = cached.id();
            destinationUrl = cached.originalUrl();
        } else {
            // 2. Cache miss -> Database query (Authoritative source of truth)
            Url url = urlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new UrlNotFoundException(shortCode));

            // Enforce lifecycle rules on database record
            if (!url.isActive() || url.isExpired()) {
                log.info("Database shortCode '{}' is inactive or expired", shortCode);
                throw new UrlExpiredOrInactiveException(shortCode);
            }

            targetId = url.getId();
            destinationUrl = url.getOriginalUrl();

            // Populate cache with authoritative record
            urlCacheService.put(new CachedUrlRecord(
                    url.getId(),
                    url.getShortCode(),
                    url.getOriginalUrl(),
                    url.getExpiresAt(),
                    url.isActive()
            ));
        }

        // 3. Record click analytics (safe & isolated from redirect)
        try {
            analyticsService.recordClick(targetId, referrer, userAgent);
        } catch (Exception ex) {
            log.error("Failed to record analytics for URL id {}: {}", targetId, ex.getMessage());
        }

        return destinationUrl;
    }
}
