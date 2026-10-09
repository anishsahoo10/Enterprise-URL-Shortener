package com.example.urlshortener.exception;

public class UrlExpiredOrInactiveException extends RuntimeException {
    public UrlExpiredOrInactiveException(String shortCode) {
        super("The requested short link is expired or inactive: " + shortCode);
    }
}
