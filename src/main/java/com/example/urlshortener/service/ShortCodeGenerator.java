package com.example.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int CODE_LENGTH = 7;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a random 7-character code from a URL-safe Base62 alphabet.
     * With 62^7 = ~3.52 trillion possibilities.
     */
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}
