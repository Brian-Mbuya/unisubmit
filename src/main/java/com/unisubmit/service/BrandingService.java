package com.unisubmit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.domain.BrandingSettings;
import com.unisubmit.domain.ThemeStylePreset;
import com.unisubmit.repository.BrandingSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class BrandingService {

    /**
     * Every colour custom property in base.css that a school may override.
     * <p>
     * The original set stopped at the 21 core brand/surface/text tokens, which left
     * accents, semantic states and the tinted badge backgrounds hard-coded to the stock
     * teal — so a rebranded deployment still leaked its origin through every status
     * badge and callout. Those are included now; the non-colour properties (typeface,
     * radii, shadows) come from {@link ThemeStylePreset} instead of the client.
     */
    private static final Set<String> ALLOWED_TOKENS = Set.of(
            "--primary", "--primary-strong",
            "--brand", "--brand-strong",
            "--gold", "--gold-bright", "--gold-dim",
            "--accent", "--accent-strong",
            "--canvas", "--bg",
            "--surface", "--surface-solid", "--surface-muted", "--surface-2", "--bg-elevated",
            "--text", "--text-muted", "--text-subtle", "--text-on-brand",
            "--border", "--border-muted", "--border-strong",
            // Semantic states — harmonised to the brand hue so success/danger/warning
            // read as part of the palette rather than stock defaults.
            "--success", "--danger", "--danger-strong", "--warning",
            // Tinted badge/callout backgrounds.
            "--tint-blue-bg", "--tint-green-bg", "--tint-amber-bg",
            "--tint-red-bg", "--tint-purple-bg", "--tint-gray-bg",
            "--edge-light"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern RGBA_PATTERN = Pattern.compile("^rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*(?:0|1|0?\\.\\d+)\\s*\\)$");

    /** Only base64 PNG. No SVG (script vector), no arbitrary data URI scheme. */
    private static final Pattern PNG_DATA_URI =
            Pattern.compile("^data:image/png;base64,[A-Za-z0-9+/]+={0,2}$");

    /** ~300 KB decoded. A logo far exceeding this is a photo, not a mark. */
    private static final int MAX_LOGO_CHARS = 400_000;

    private static final int MAX_SCHOOL_NAME_LENGTH = 120;

    /** The three generator inputs stored alongside the derived palette. */
    private static final Set<String> BASE_COLOR_KEYS = Set.of("primary", "secondary", "neutral");

    /** The 8-byte PNG signature every valid PNG starts with. */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final BrandingSettingsRepository repository;
    private final ObjectMapper objectMapper;

    private volatile String cachedCssBlock = null;
    private volatile String cachedCanvasColor = null;

    public BrandingService(BrandingSettingsRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Clears the cache AFTER the surrounding transaction commits.
     * <p>
     * Clearing it inline lost the write: under READ COMMITTED a concurrent page render
     * (every request reads this via {@code GlobalModelAttributes}) could land between the
     * clear and the commit, re-read the still-old row, and repopulate the cache with the
     * previous theme. Nothing clears it again until the next branding write, so the stale
     * palette would be served indefinitely while the navbar — which is uncached — already
     * showed the new name and logo. Mirrors the deferral AIInsightService already uses.
     */
    private void invalidateCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clearCacheNow();
                        }
                    });
        } else {
            clearCacheNow();
        }
    }

    private void clearCacheNow() {
        this.cachedCssBlock = null;
        this.cachedCanvasColor = null;
    }

    /**
     * Strictly validate and persist theme tokens, leaving school name and logo alone.
     */
    @Transactional
    public void saveThemeTokens(Map<String, String> inputTokens) {
        saveIdentity(null, null, null, null, sanitizeTokens(inputTokens), false);
    }

    /**
     * Persists a complete school identity in one shot — the onboarding wizard's save.
     * Name and logo are optional; passing {@code updateIdentity=false} leaves whatever is
     * already stored untouched, which is what {@link #saveThemeTokens} relies on so a
     * palette-only edit never wipes the school's name.
     */
    @Transactional
    public void saveSchoolIdentity(String schoolName, String logoPng, Map<String, String> inputTokens) {
        saveSchoolIdentity(schoolName, logoPng, null, inputTokens);
    }

    @Transactional
    public void saveSchoolIdentity(String schoolName, String logoPng, String themeStyle,
                                   Map<String, String> inputTokens) {
        saveSchoolIdentity(schoolName, logoPng, themeStyle, null, inputTokens);
    }

    @Transactional
    public void saveSchoolIdentity(String schoolName, String logoPng, String themeStyle,
                                   Map<String, String> baseColors, Map<String, String> inputTokens) {
        saveIdentity(schoolName, logoPng, themeStyle, baseColors,
                sanitizeTokens(inputTokens), true);
    }

    /**
     * The three colours the palette was generated from, for prefilling the admin form.
     * Empty map when a deployment has never saved them.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getBaseColors() {
        return repository.findById(BrandingSettings.SINGLETON_ID)
                .map(BrandingSettings::getBaseColorsJson)
                .filter(json -> json != null && !json.isBlank())
                .map(json -> {
                    try {
                        Map<String, String> parsed =
                                objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
                        // Re-validated on read, same defence-in-depth as the token block:
                        // these are echoed straight back into form value attributes.
                        Map<String, String> safe = new LinkedHashMap<>();
                        parsed.forEach((k, v) -> {
                            if (BASE_COLOR_KEYS.contains(k) && v != null
                                    && HEX_PATTERN.matcher(v.trim()).matches()) {
                                safe.put(k, v.trim());
                            }
                        });
                        return safe;
                    } catch (Exception e) {
                        return Map.<String, String>of();
                    }
                })
                .orElseGet(Map::of);
    }

    private String serializeBaseColors(Map<String, String> baseColors) {
        if (baseColors == null || baseColors.isEmpty()) {
            return null;
        }
        Map<String, String> safe = new LinkedHashMap<>();
        for (String key : BASE_COLOR_KEYS) {
            String val = baseColors.get(key);
            if (val != null && HEX_PATTERN.matcher(val.trim()).matches()) {
                safe.put(key, val.trim());
            }
        }
        if (safe.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveIdentity(String schoolName, String logoPng, String themeStyle,
                              Map<String, String> baseColors,
                              Map<String, String> sanitizedTokens, boolean updateIdentity) {
        String cleanName = updateIdentity ? validateSchoolName(schoolName) : null;
        String cleanLogo = updateIdentity ? validateLogo(logoPng) : null;
        // Unknown names fall back to the default rather than throwing: the preset list is
        // ours to change, and a stale form value should not block a save.
        String cleanStyle = updateIdentity
                ? ThemeStylePreset.fromNameOrDefault(themeStyle).name() : null;

        try {
            String jsonStr = objectMapper.writeValueAsString(sanitizedTokens);
            BrandingSettings settings = repository.findById(BrandingSettings.SINGLETON_ID)
                    .orElseGet(() -> {
                        BrandingSettings fresh = new BrandingSettings();
                        fresh.setId(BrandingSettings.SINGLETON_ID);
                        return fresh;
                    });
            settings.setTokensJson(jsonStr);
            if (updateIdentity) {
                settings.setSchoolName(cleanName);
                settings.setThemeStyle(cleanStyle);
                // Absent means "unchanged", mirroring the logo rule.
                String cleanBase = serializeBaseColors(baseColors);
                if (cleanBase != null) {
                    settings.setBaseColorsJson(cleanBase);
                }
                // A wizard save with no new file keeps the existing logo rather than
                // clearing it — re-uploading the same PNG just to rename is busywork.
                if (cleanLogo != null) {
                    settings.setLogoPng(cleanLogo);
                }
            }
            settings.setUpdatedAt(Instant.now());
            repository.save(settings);
            invalidateCache();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize theme tokens", e);
        }
    }

    private Map<String, String> sanitizeTokens(Map<String, String> inputTokens) {
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
        return sanitized;
    }

    /**
     * @return the trimmed name, or null when blank. Control characters are rejected
     *         rather than stripped so a name that renders differently than it was typed
     *         is never silently stored. Output escaping is Thymeleaf's job; this is the
     *         input-side half.
     */
    private String validateSchoolName(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) {
            return null;
        }
        String trimmed = schoolName.trim();
        if (trimmed.length() > MAX_SCHOOL_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "School name must be " + MAX_SCHOOL_NAME_LENGTH + " characters or fewer.");
        }
        for (char c : trimmed.toCharArray()) {
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("School name contains invalid control characters.");
            }
        }
        return trimmed;
    }

    /**
     * Validates the logo ENVELOPE only — the bytes are never decoded into an image here.
     * That is the whole security posture of this feature: no server-side image parser is
     * ever pointed at admin-supplied input, so image-library CVEs are not reachable.
     *
     * @return the validated data URI, or null when none was supplied.
     */
    private String validateLogo(String logoPng) {
        if (logoPng == null || logoPng.isBlank()) {
            return null;
        }
        String trimmed = logoPng.trim();
        if (trimmed.length() > MAX_LOGO_CHARS) {
            throw new IllegalArgumentException(
                    "Logo is too large — re-export it under ~300 KB.");
        }
        if (!PNG_DATA_URI.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Logo must be a base64 PNG data URI. SVG and other formats are converted "
                            + "in the browser before upload.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed.substring("data:image/png;base64,".length()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Logo is not valid base64.");
        }
        if (decoded.length < PNG_MAGIC.length) {
            throw new IllegalArgumentException("Logo is not a valid PNG.");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (decoded[i] != PNG_MAGIC[i]) {
                // The declared MIME type disagrees with the actual bytes.
                throw new IllegalArgumentException("Logo is not a valid PNG.");
            }
        }
        return trimmed;
    }

    /** The stored style preset, for pre-selecting it in the admin form. */
    @Transactional(readOnly = true)
    public ThemeStylePreset getThemeStyle() {
        return repository.findById(BrandingSettings.SINGLETON_ID)
                .map(s -> ThemeStylePreset.fromNameOrDefault(s.getThemeStyle()))
                .orElseGet(ThemeStylePreset::defaultPreset);
    }

    /** The institution name for the navbar, or null to fall back to "UniSubmit". */
    @Transactional(readOnly = true)
    public String getSchoolName() {
        return repository.findById(BrandingSettings.SINGLETON_ID)
                .map(BrandingSettings::getSchoolName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    /** Re-validated on read, for the same reason {@link #getSanitizedCssBlock} is. */
    @Transactional(readOnly = true)
    public String getLogoDataUri() {
        return repository.findById(BrandingSettings.SINGLETON_ID)
                .map(BrandingSettings::getLogoPng)
                .filter(logo -> logo != null && !logo.isBlank()
                        && logo.length() <= MAX_LOGO_CHARS
                        && PNG_DATA_URI.matcher(logo).matches())
                .orElse(null);
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

            // Typeface, corner geometry and elevation. Sourced from the enum, never from
            // the stored row, so these can never carry injected CSS however the row was
            // written.
            css.append(ThemeStylePreset
                    .fromNameOrDefault(settingsOpt.get().getThemeStyle())
                    .toCssDeclarations());

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
