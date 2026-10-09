package com.example.urlshortener.service;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.dto.ClickEventDto;
import com.example.urlshortener.entity.ClickEvent;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int MAX_REFERRER_LENGTH = 1024;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(UrlRepository urlRepository, ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    /**
     * Records a click event and performs an atomic database-level increment on clickCount.
     * Exceptions are isolated and logged so that redirect availability is not compromised.
     */
    @Transactional
    public void recordClick(Long urlId, String rawReferrer, String rawUserAgent) {
        try {
            // Atomic database increment to avoid lost updates under concurrent redirects
            urlRepository.incrementClickCount(urlId);

            String sanitizedReferrer = truncate(rawReferrer, MAX_REFERRER_LENGTH);
            String sanitizedUserAgent = truncate(rawUserAgent, MAX_USER_AGENT_LENGTH);

            Url urlReference = urlRepository.getReferenceById(urlId);
            ClickEvent clickEvent = new ClickEvent(urlReference, Instant.now(), sanitizedReferrer, sanitizedUserAgent);
            clickEventRepository.save(clickEvent);
            log.debug("Recorded click event for URL id: {}", urlId);
        } catch (Exception ex) {
            log.error("Failed to record click event for URL id {}: {}", urlId, ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode, int page, int size) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ClickEvent> eventPage = clickEventRepository.findByUrlIdOrderByClickedAtDesc(url.getId(), pageRequest);

        List<ClickEventDto> dtos = eventPage.getContent().stream()
                .map(event -> new ClickEventDto(event.getClickedAt(), event.getReferrer()))
                .toList();

        return new AnalyticsResponse(
                url.getShortCode(),
                url.getClickCount(),
                dtos,
                eventPage.getNumber(),
                eventPage.getSize(),
                eventPage.getTotalElements()
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
