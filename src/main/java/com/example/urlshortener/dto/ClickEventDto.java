package com.example.urlshortener.dto;

import java.time.Instant;

public record ClickEventDto(
        Instant clickedAt,
        String referrer
) {
}
