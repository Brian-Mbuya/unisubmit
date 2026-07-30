package com.unisubmit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Phase 7 — OCR fallback for scanned PDFs.
 * <p>
 * When Tika extracts (almost) no text — the classic scanned-document case —
 * the pipeline can post the file to a sidecar OCR endpoint instead of failing
 * outright. Disabled by default ({@code unisubmit.ocr.enabled}); the sidecar
 * contract is {@code POST /ocr} with JSON {@code {filename, data(base64)}}
 * returning {@code {text}} (see specter-service/app.py).
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${unisubmit.ocr.enabled:false}")
    private boolean enabled;

    @Value("${unisubmit.ocr.url:http://localhost:5001}")
    private String baseUrl;

    public boolean isEnabled() {
        return enabled;
    }

    /** Extracted text via OCR, or empty when disabled/unavailable/failed. */
    public Optional<String> extractText(File file) {
        if (!enabled || file == null || !file.exists()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String body = "{\"filename\":\"" + file.getName().replace("\"", "")
                    + "\",\"data\":\"" + Base64.getEncoder().encodeToString(bytes) + "\"}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/ocr"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OCR sidecar answered {} for {}", response.statusCode(), file.getName());
                return Optional.empty();
            }
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            String text = root.path("text").asText("");
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception ex) {
            log.warn("OCR extraction failed for {}: {}", file.getName(), ex.getMessage());
            return Optional.empty();
        }
    }
}
