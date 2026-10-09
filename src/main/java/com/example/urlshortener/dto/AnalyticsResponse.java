package com.example.urlshortener.dto;

import java.util.List;

public record AnalyticsResponse(
        String shortCode,
        long totalClicks,
        List<ClickEventDto> events,
        int page,
        int size,
        long totalEvents
) {
}
