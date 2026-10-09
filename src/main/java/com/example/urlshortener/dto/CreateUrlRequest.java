package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(
        @NotBlank(message = "Original URL cannot be blank")
        @Size(max = 2048, message = "Original URL must not exceed 2048 characters")
        @Pattern(
                regexp = "^https?://.+",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Must be a valid HTTP or HTTPS URL"
        )
        String originalUrl,

        Instant expiresAt
) {
}
