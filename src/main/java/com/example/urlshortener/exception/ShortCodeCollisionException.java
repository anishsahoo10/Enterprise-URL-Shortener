package com.example.urlshortener.exception;

public class ShortCodeCollisionException extends RuntimeException {
    public ShortCodeCollisionException(String message) {
        super(message);
    }
}
