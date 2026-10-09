package com.example.urlshortener.dto;

import java.io.Serializable;
import java.time.Instant;

public record CachedUrlRecord(
        Long id,
        String shortCode,
        String originalUrl,
        Instant expiresAt,
        boolean active
) implements Serializable {

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
