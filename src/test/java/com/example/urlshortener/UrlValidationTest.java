package com.example.urlshortener;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UrlValidationTest {

    private UrlService urlService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        UrlRepository urlRepository = mock(UrlRepository.class);
        UrlCacheService urlCacheService = mock(UrlCacheService.class);
        ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
        urlService = new UrlService(urlRepository, urlCacheService, generator, "http://localhost:8080");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com/path?query=1&another=2",
            "https://sub.domain.co.uk/test#section",
            "http://127.0.0.1:8080/resource"
    })
    @DisplayName("Valid HTTP and HTTPS URLs should pass validation")
    void shouldAcceptValidUrls(String validUrl) {
        urlService.validateDestinationUrl(validUrl);

        CreateUrlRequest request = new CreateUrlRequest(validUrl, null);
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "ftp://files.example.com",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,<script>alert(1)</script>",
            "mailto:user@example.com",
            "not-a-valid-url"
    })
    @DisplayName("Blank, malformed, and non-HTTP/HTTPS schemes must be rejected")
    void shouldRejectInvalidUrls(String invalidUrl) {
        assertThatThrownBy(() -> urlService.validateDestinationUrl(invalidUrl))
                .isInstanceOf(InvalidUrlException.class);

        CreateUrlRequest request = new CreateUrlRequest(invalidUrl, null);
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("Expired timestamp in past should be rejected")
    void shouldRejectPastExpiry() {
        CreateUrlRequest request = new CreateUrlRequest(
                "https://example.com",
                Instant.now().minusSeconds(3600)
        );

        assertThatThrownBy(() -> urlService.createUrl(request))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("Expiry time must be in the future");
    }
}
