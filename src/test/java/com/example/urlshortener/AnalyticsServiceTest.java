package com.example.urlshortener;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.entity.ClickEvent;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private UrlRepository urlRepository;
    private ClickEventRepository clickEventRepository;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        clickEventRepository = mock(ClickEventRepository.class);
        analyticsService = new AnalyticsService(urlRepository, clickEventRepository);
    }

    @Test
    @DisplayName("recordClick performs atomic increment and saves sanitized click event")
    void shouldRecordClickWithSanitizedInputs() {
        Url urlRef = new Url();
        urlRef.setId(10L);
        when(urlRepository.getReferenceById(10L)).thenReturn(urlRef);

        String longReferrer = "https://example.com/" + "a".repeat(1500);
        String longUserAgent = "Mozilla/" + "b".repeat(700);

        analyticsService.recordClick(10L, longReferrer, longUserAgent);

        verify(urlRepository, times(1)).incrementClickCount(10L);

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, times(1)).save(captor.capture());

        ClickEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getReferrer()).hasSize(1024);
        assertThat(savedEvent.getUserAgent()).hasSize(512);
        assertThat(savedEvent.getClickedAt()).isNotNull();
    }

    @Test
    @DisplayName("getAnalytics retrieves aggregate click count and paginated events")
    void shouldGetAnalyticsSummaryAndEvents() {
        Url url = new Url("stats12", "https://example.com", Instant.now(), null);
        url.setId(7L);
        url.setClickCount(42L);

        when(urlRepository.findByShortCode("stats12")).thenReturn(Optional.of(url));

        ClickEvent event1 = new ClickEvent(url, Instant.now(), "https://google.com", "Browser");
        when(clickEventRepository.findByUrlIdOrderByClickedAtDesc(eq(7L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event1), PageRequest.of(0, 10), 42));

        AnalyticsResponse response = analyticsService.getAnalytics("stats12", 0, 10);

        assertThat(response.shortCode()).isEqualTo("stats12");
        assertThat(response.totalClicks()).isEqualTo(42L);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).referrer()).isEqualTo("https://google.com");
        assertThat(response.totalEvents()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getAnalytics throws UrlNotFoundException for non-existent short code")
    void shouldThrowNotFoundWhenShortCodeMissing() {
        when(urlRepository.findByShortCode("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.getAnalytics("none", 0, 20))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
