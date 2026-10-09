package com.example.urlshortener.service;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.UpdateUrlRequest;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortCodeCollisionException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final int MAX_COLLISION_RETRIES = 5;

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final ShortCodeGenerator shortCodeGenerator;
    private final String baseUrl;

    public UrlService(
            UrlRepository urlRepository,
            UrlCacheService urlCacheService,
            ShortCodeGenerator shortCodeGenerator,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.urlCacheService = urlCacheService;
        this.shortCodeGenerator = shortCodeGenerator;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public UrlResponse createUrl(CreateUrlRequest request) {
        validateDestinationUrl(request.originalUrl());

        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new InvalidUrlException("Expiry time must be in the future");
        }

        // Bounded retry loop handling rare shortCode collisions against DB unique constraint
        for (int attempt = 1; attempt <= MAX_COLLISION_RETRIES; attempt++) {
            String shortCode = shortCodeGenerator.generate();
            try {
                Url url = new Url(shortCode, request.originalUrl().trim(), Instant.now(), request.expiresAt());
                Url saved = urlRepository.saveAndFlush(url);
                log.info("Created short URL {} for destination {}", shortCode, request.originalUrl());
                return toUrlResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Short code collision detected for code '{}' on attempt {}/{}. Retrying...",
                        shortCode, attempt, MAX_COLLISION_RETRIES);
            }
        }

        throw new ShortCodeCollisionException("Failed to generate a unique short code after " + MAX_COLLISION_RETRIES + " attempts");
    }

    @Transactional(readOnly = true)
    public UrlDetailsResponse getUrlDetails(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return toUrlDetailsResponse(url);
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listUrls(Pageable pageable) {
        return urlRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toUrlResponse);
    }

    @Transactional
    public UrlDetailsResponse updateUrl(String shortCode, UpdateUrlRequest request) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (request.originalUrl() != null && !request.originalUrl().isBlank()) {
            validateDestinationUrl(request.originalUrl());
            url.setOriginalUrl(request.originalUrl().trim());
        }

        if (request.active() != null) {
            url.setActive(request.active());
        }

        if (request.expiresAt() != null) {
            url.setExpiresAt(request.expiresAt());
        }

        Url updated = urlRepository.saveAndFlush(url);

        // Invalidate Redis cache entry on update/deactivation
        urlCacheService.evict(shortCode);

        log.info("Updated short URL {}", shortCode);
        return toUrlDetailsResponse(updated);
    }

    @Transactional
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        urlRepository.delete(url);

        // Evict from Redis cache
        urlCacheService.evict(shortCode);
        log.info("Deleted short URL {}", shortCode);
    }

    public void validateDestinationUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("Must be a valid HTTP or HTTPS URL");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("Must be a valid HTTP or HTTPS URL");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidUrlException("Must be a valid HTTP or HTTPS URL");
            }
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Must be a valid HTTP or HTTPS URL");
        }
    }

    private UrlResponse toUrlResponse(Url url) {
        String fullShortUrl = baseUrl + "/" + url.getShortCode();
        return new UrlResponse(
                url.getShortCode(),
                fullShortUrl,
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                url.isActive()
        );
    }

    private UrlDetailsResponse toUrlDetailsResponse(Url url) {
        String fullShortUrl = baseUrl + "/" + url.getShortCode();
        return new UrlDetailsResponse(
                url.getShortCode(),
                fullShortUrl,
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                url.isActive(),
                url.getClickCount()
        );
    }
}
