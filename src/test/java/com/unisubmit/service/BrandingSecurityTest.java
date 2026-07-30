package com.unisubmit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.domain.BrandingSettings;
import com.unisubmit.repository.BrandingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BrandingSecurityTest {

    @Autowired
    private BrandingService brandingService;

    @Autowired
    private BrandingSettingsRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        brandingService.resetToDefault();
    }

    @Test
    void saveThemeTokens_WithValidTokens_SavesSuccessfully() {
        Map<String, String> validTokens = Map.of(
                "--primary", "#5FBFAB",
                "--brand", "#35A08C",
                "--canvas", "#121417",
                "--surface", "#1A1D21",
                "--gold-dim", "rgba(205, 166, 96, 0.32)"
        );

        assertDoesNotThrow(() -> brandingService.saveThemeTokens(validTokens));

        String cssBlock = brandingService.getSanitizedCssBlock();
        assertTrue(cssBlock.contains("--primary: #5FBFAB;"));
        assertTrue(cssBlock.contains("--brand: #35A08C;"));
        assertEquals("#121417", brandingService.getCanvasColor());
    }

    @Test
    void saveThemeTokens_WithUnauthorizedKey_RejectsSave() {
        Map<String, String> maliciousMap = Map.of(
                "--primary", "#5FBFAB",
                "--malicious-key", "#123456"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                brandingService.saveThemeTokens(maliciousMap)
        );

        assertTrue(ex.getMessage().contains("Unauthorized theme token key"));
    }

    @Test
    void saveThemeTokens_WithCssInjectionPayloadInValue_RejectsSave() {
        Map<String, String> injectionMap = Map.of(
                "--brand", "#35A08C;} body { display: none; }"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                brandingService.saveThemeTokens(injectionMap)
        );

        assertTrue(ex.getMessage().contains("Invalid color value format"));
    }

    @Test
    void getSanitizedCssBlock_WithCorruptedDatabaseRow_FiltersOutMaliciousPayloadsOnRead() throws Exception {
        Map<String, String> corruptedRow = new HashMap<>();
        corruptedRow.put("--brand", "#35A08C");
        corruptedRow.put("--canvas", "#121417");
        corruptedRow.put("--unauthorized", "#000000");
        corruptedRow.put("--primary", "red;} body { color: red; }");

        String corruptedJson = objectMapper.writeValueAsString(corruptedRow);
        repository.save(new BrandingSettings(BrandingSettings.SINGLETON_ID, corruptedJson, java.time.Instant.now()));

        String cssBlock = brandingService.getSanitizedCssBlock();

        assertTrue(cssBlock.contains("--brand: #35A08C;"));
        assertTrue(cssBlock.contains("--canvas: #121417;"));
        assertFalse(cssBlock.contains("--unauthorized"));
        assertFalse(cssBlock.contains("body { color: red; }"));
    }

    @Test
    void resetToDefault_ClearsBrandingRowAndHandlesDoubleResetSafely() {
        Map<String, String> validTokens = Map.of("--primary", "#5FBFAB");
        brandingService.saveThemeTokens(validTokens);

        assertFalse(brandingService.getSanitizedCssBlock().isEmpty());

        // First reset
        assertDoesNotThrow(() -> brandingService.resetToDefault());
        assertEquals("", brandingService.getSanitizedCssBlock());
        assertEquals("#121417", brandingService.getCanvasColor());

        // Second reset (double-reset on absent row)
        assertDoesNotThrow(() -> brandingService.resetToDefault());
        assertEquals("", brandingService.getSanitizedCssBlock());
    }
}
