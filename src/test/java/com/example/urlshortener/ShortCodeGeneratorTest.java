package com.example.urlshortener;

import com.example.urlshortener.service.ShortCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("Short code should have length 7 and use Base62 characters only")
    void shouldGenerateValidSevenCharBase62Code() {
        for (int i = 0; i < 1000; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(7);
            assertThat(code).matches("^[a-zA-Z0-9]{7}$");
        }
    }

    @Test
    @DisplayName("Generated codes should have high randomness and variety")
    void shouldGenerateDiverseCodes() {
        Set<String> generatedCodes = new HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            generatedCodes.add(generator.generate());
        }
        // With 62^7 space, 1000 random codes should be distinct
        assertThat(generatedCodes).hasSize(count);
    }
}
