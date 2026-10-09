package com.example.urlshortener.dto;

import java.time.Instant;

public record UrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean active
) {
}
