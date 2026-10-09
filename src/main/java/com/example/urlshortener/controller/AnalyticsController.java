package com.example.urlshortener.controller;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls/{shortCode}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Page index must be >= 0 and size must be > 0");
        }
        AnalyticsResponse response = analyticsService.getAnalytics(shortCode, page, size);
        return ResponseEntity.ok(response);
    }
}
