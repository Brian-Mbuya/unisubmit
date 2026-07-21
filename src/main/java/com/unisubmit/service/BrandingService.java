package com.unisubmit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.domain.BrandingSettings;
import com.unisubmit.repository.BrandingSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class BrandingService {

    private static final Set<String> ALLOWED_TOKENS = Set.of(
            "--primary", "--primary-strong",
            "--brand", "--brand-strong",
            "--gold", "--gold-bright", "--gold-dim",
            "--canvas", "--bg",
            "--surface", "--surface-solid", "--surface-muted", "--surface-2", "--bg-elevated",
            "--text", "--text-muted", "--text-subtle", "--text-on-brand",
            "--border", "--border-muted", "--border-strong"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern RGBA_PATTERN = Pattern.compile("^rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*(?:0|1|0?\\.\\d+)\\s*\\)$");

    private final BrandingSettingsRepository repository;
    private final ObjectMapper objectMapper;

    private volatile String cachedCssBlock = null;
    private volatile String cachedCanvasColor = null;

    public BrandingService(BrandingSettingsRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    private void invalidateCache() {
        this.cachedCssBlock = null;
        this.cachedCanvasColor = null;
    }

    /**
     * Strictly validate and persist theme tokens.
     */
    @Transactional
    public void saveThemeTokens(Map<String, String> inputTokens) {
        if (inputTokens == null || inputTokens.isEmpty()) {
            throw new IllegalArgumentException("Theme tokens cannot be empty");
        }

        Map<String, String> sanitized = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : inputTokens.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().trim() : "";

            if (!ALLOWED_TOKENS.contains(key)) {
                throw new IllegalArgumentException("Unauthorized theme token key: " + key);
            }

            if (!HEX_PATTERN.matcher(value).matches() && !RGBA_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid color value format for " + key + ": " + value);
            }

            sanitized.put(key, value);
        }

        try {
            String jsonStr = objectMapper.writeValueAsString(sanitized);
            BrandingSettings settings = repository.findById(BrandingSettings.SINGLETON_ID)
                    .orElse(new BrandingSettings(BrandingSettings.SINGLETON_ID, jsonStr, Instant.now()));
            settings.setTokensJson(jsonStr);
            settings.setUpdatedAt(Instant.now());
            repository.save(settings);
            invalidateCache();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize theme tokens", e);
        }
    }

    /**
     * Read-time defense-in-depth: construct sanitized CSS :root { ... } block (cached in memory).
     */
    @Transactional(readOnly = true)
    public String getSanitizedCssBlock() {
        String cached = this.cachedCssBlock;
        if (cached != null) {
            return cached;
        }

        Optional<BrandingSettings> settingsOpt = repository.findById(BrandingSettings.SINGLETON_ID);
        if (settingsOpt.isEmpty()) {
            this.cachedCssBlock = "";
            return "";
        }

        String jsonStr = settingsOpt.get().getTokensJson();
        if (jsonStr == null || jsonStr.isBlank()) {
            this.cachedCssBlock = "";
            return "";
        }

        try {
            Map<String, String> tokens = objectMapper.readValue(jsonStr, new TypeReference<Map<String, String>>() {});
            StringBuilder css = new StringBuilder(":root {\n");

            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue() != null ? entry.getValue().trim() : "";

                if (ALLOWED_TOKENS.contains(key) && (HEX_PATTERN.matcher(val).matches() || RGBA_PATTERN.matcher(val).matches())) {
                    css.append("  ").append(key).append(": ").append(val).append(";\n");
                }
            }

            css.append("}");
            String result = css.toString();
            this.cachedCssBlock = result;
            return result;
        } catch (Exception e) {
            this.cachedCssBlock = "";
            return "";
        }
    }

    /**
     * Extract canvas background color for theme-color meta tag (cached in memory).
     */
    @Transactional(readOnly = true)
    public String getCanvasColor() {
        String cached = this.cachedCanvasColor;
        if (cached != null) {
            return cached;
        }

        Optional<BrandingSettings> settingsOpt = repository.findById(BrandingSettings.SINGLETON_ID);
        if (settingsOpt.isEmpty()) {
            this.cachedCanvasColor = "#121417";
            return "#121417";
        }

        try {
            Map<String, String> tokens = objectMapper.readValue(settingsOpt.get().getTokensJson(), new TypeReference<Map<String, String>>() {});
            String canvas = tokens.get("--canvas");
            if (canvas != null && HEX_PATTERN.matcher(canvas.trim()).matches()) {
                String val = canvas.trim();
                this.cachedCanvasColor = val;
                return val;
            }
        } catch (Exception ignored) {
        }
        this.cachedCanvasColor = "#121417";
        return "#121417";
    }

    /**
     * Reset branding back to defaults by removing the single-row configuration.
     * Safely guarded against double-reset exceptions.
     */
    @Transactional
    public void resetToDefault() {
        if (repository.existsById(BrandingSettings.SINGLETON_ID)) {
            repository.deleteById(BrandingSettings.SINGLETON_ID);
        }
        invalidateCache();
    }
}
