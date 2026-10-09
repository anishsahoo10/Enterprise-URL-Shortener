package com.example.urlshortener.controller;

import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.UpdateUrlRequest;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    public ResponseEntity<UrlResponse> createUrl(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlService.createUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlDetailsResponse> getUrlDetails(@PathVariable String shortCode) {
        UrlDetailsResponse response = urlService.getUrlDetails(shortCode);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<UrlResponse>> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UrlResponse> response = urlService.listUrls(pageable);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{shortCode}")
    public ResponseEntity<UrlDetailsResponse> updateUrl(
            @PathVariable String shortCode,
            @Valid @RequestBody UpdateUrlRequest request) {
        UrlDetailsResponse response = urlService.updateUrl(shortCode, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
