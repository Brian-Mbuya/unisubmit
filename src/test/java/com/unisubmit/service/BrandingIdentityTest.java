package com.unisubmit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * School-identity half of the branding wizard. The logo is the interesting surface: it is
 * the only admin-supplied binary the app stores, and it gets inlined into every page, so
 * an SVG smuggled past validation would be stored XSS on every request. These pin the
 * PNG-only envelope check that prevents that.
 */
@SpringBootTest
class BrandingIdentityTest {

    /** A real 1x1 PNG - correct magic bytes, so it exercises the success path. */
    private static final String VALID_PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42m"
                    + "P8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final Map<String, String> VALID_TOKENS =
            Map.of("--primary", "#35A08C", "--canvas", "#121417", "--text", "#F2EFE6");

    @Autowired private BrandingService brandingService;

    @AfterEach
    void tearDown() {
        brandingService.resetToDefault();
    }

    @Test
    void savesSchoolNameAndLogo() {
        brandingService.saveSchoolIdentity("Chuka University", VALID_PNG, VALID_TOKENS);

        assertEquals("Chuka University", brandingService.getSchoolName());
        assertEquals(VALID_PNG, brandingService.getLogoDataUri());
    }

    @Test
    void rejectsSvgLogoBecauseItCanCarryScript() {
        String svg = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> brandingService.saveSchoolIdentity("Evil", svg, VALID_TOKENS));
    }

    @Test
    void rejectsNonPngBytesEvenWhenTheMimeTypeClaimsPng() {
        // Correct prefix, valid base64 - but the payload is not a PNG. Catching this
        // requires actually checking the magic bytes, not just the declared type.
        String liar = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> brandingService.saveSchoolIdentity("Liar", liar, VALID_TOKENS));
    }

    @Test
    void rejectsOversizedLogo() {
        String huge = "data:image/png;base64," + "A".repeat(400_001);

        assertThrows(IllegalArgumentException.class,
                () -> brandingService.saveSchoolIdentity("Huge", huge, VALID_TOKENS));
    }

    @Test
    void rejectsSchoolNameWithControlCharacters() {
        // Built via char cast rather than a unicode escape: javac expands those before
        // lexing, so writing one inline is easy to get subtly wrong.
        String withControlChar = "Evil" + (char) 7 + "University";

        assertThrows(IllegalArgumentException.class,
                () -> brandingService.saveSchoolIdentity(withControlChar, VALID_PNG, VALID_TOKENS));
    }

    @Test
    void rejectsOverlongSchoolName() {
        assertThrows(IllegalArgumentException.class,
                () -> brandingService.saveSchoolIdentity("A".repeat(121), VALID_PNG, VALID_TOKENS));
    }

    @Test
    void paletteOnlySaveKeepsTheExistingSchoolIdentity() {
        brandingService.saveSchoolIdentity("Kenyatta University", VALID_PNG, VALID_TOKENS);

        // The legacy palette-only path must not wipe name/logo - otherwise tweaking a
        // color would silently un-brand the deployment.
        brandingService.saveThemeTokens(Map.of("--primary", "#AA0000"));

        assertEquals("Kenyatta University", brandingService.getSchoolName());
        assertEquals(VALID_PNG, brandingService.getLogoDataUri());
    }

    @Test
    void renamingWithoutReuploadingKeepsTheStoredLogo() {
        brandingService.saveSchoolIdentity("Old Name", VALID_PNG, VALID_TOKENS);
        brandingService.saveSchoolIdentity("New Name", null, VALID_TOKENS);

        assertEquals("New Name", brandingService.getSchoolName());
        assertEquals(VALID_PNG, brandingService.getLogoDataUri());
    }

    @Test
    void resetClearsIdentityAndCss() {
        brandingService.saveSchoolIdentity("Temp University", VALID_PNG, VALID_TOKENS);
        brandingService.resetToDefault();

        assertNull(brandingService.getSchoolName());
        assertNull(brandingService.getLogoDataUri());
        assertTrue(brandingService.getSanitizedCssBlock().isEmpty());
    }
}
