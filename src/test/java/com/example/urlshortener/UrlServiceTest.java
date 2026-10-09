package com.example.urlshortener;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.UpdateUrlRequest;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.ShortCodeCollisionException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UrlServiceTest {

    private UrlRepository urlRepository;
    private UrlCacheService urlCacheService;
    private ShortCodeGenerator shortCodeGenerator;
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        urlCacheService = mock(UrlCacheService.class);
        shortCodeGenerator = mock(ShortCodeGenerator.class);
        urlService = new UrlService(urlRepository, urlCacheService, shortCodeGenerator, "http://localhost:8080");
    }

    @Test
    @DisplayName("Successfully creates URL when no collisions occur")
    void shouldCreateUrlSuccessfully() {
        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        Url savedUrl = new Url("abc1234", "https://example.com/products", Instant.now(), null);
        savedUrl.setId(1L);

        when(urlRepository.saveAndFlush(any(Url.class))).thenReturn(savedUrl);

        CreateUrlRequest request = new CreateUrlRequest("https://example.com/products", null);
        UrlResponse response = urlService.createUrl(request);

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/products");
        assertThat(response.active()).isTrue();
        verify(urlRepository, times(1)).saveAndFlush(any(Url.class));
    }

    @Test
    @DisplayName("Retries on collision and succeeds if subsequent code is unique")
    void shouldRetryOnCollisionAndSucceed() {
        when(shortCodeGenerator.generate())
                .thenReturn("collide1")
                .thenReturn("collide2")
                .thenReturn("unique3");

        Url savedUrl = new Url("unique3", "https://example.com", Instant.now(), null);
        savedUrl.setId(2L);

        when(urlRepository.saveAndFlush(any(Url.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"))
                .thenReturn(savedUrl);

        CreateUrlRequest request = new CreateUrlRequest("https://example.com", null);
        UrlResponse response = urlService.createUrl(request);

        assertThat(response.shortCode()).isEqualTo("unique3");
        verify(urlRepository, times(3)).saveAndFlush(any(Url.class));
    }

    @Test
    @DisplayName("Throws ShortCodeCollisionException when max retry limit is exhausted")
    void shouldThrowExceptionWhenCollisionsExhausted() {
        when(shortCodeGenerator.generate()).thenReturn("collideX");
        when(urlRepository.saveAndFlush(any(Url.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        CreateUrlRequest request = new CreateUrlRequest("https://example.com", null);

        assertThatThrownBy(() -> urlService.createUrl(request))
                .isInstanceOf(ShortCodeCollisionException.class)
                .hasMessageContaining("Failed to generate a unique short code");

        verify(urlRepository, times(5)).saveAndFlush(any(Url.class));
    }

    @Test
    @DisplayName("Updating URL modifies entity, saves, and evicts cache")
    void shouldUpdateUrlAndEvictCache() {
        Url existing = new Url("abc1234", "https://old.com", Instant.now(), null);
        existing.setId(10L);

        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existing));
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUrlRequest request = new UpdateUrlRequest("https://new.com", false, null);
        UrlDetailsResponse response = urlService.updateUrl("abc1234", request);

        assertThat(response.originalUrl()).isEqualTo("https://new.com");
        assertThat(response.active()).isFalse();
        verify(urlCacheService, times(1)).evict("abc1234");
    }

    @Test
    @DisplayName("Deleting URL removes entity and evicts cache")
    void shouldDeleteUrlAndEvictCache() {
        Url existing = new Url("abc1234", "https://example.com", Instant.now(), null);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existing));

        urlService.deleteUrl("abc1234");

        verify(urlRepository, times(1)).delete(existing);
        verify(urlCacheService, times(1)).evict("abc1234");
    }

    @Test
    @DisplayName("Throws UrlNotFoundException when URL does not exist")
    void shouldThrowNotFoundWhenMissing() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.getUrlDetails("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
