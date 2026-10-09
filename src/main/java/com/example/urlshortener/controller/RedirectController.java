package com.example.urlshortener.controller;

import com.example.urlshortener.service.RedirectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]{4,16}}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {

        String destinationUrl = redirectService.resolveAndTrack(shortCode, referrer, userAgent);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(destinationUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
