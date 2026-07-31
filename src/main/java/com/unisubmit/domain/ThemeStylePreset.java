package com.unisubmit.domain;

/**
 * The shape half of a school's identity: corner geometry and elevation.
 * <p>
 * Colour alone cannot make two deployments look like different products — recolouring
 * leaves the same shapes and the same weight of shadow, so both still read as "the same
 * app in a different hue". Typography lives in {@link ThemeFontPreset}; keeping the two
 * axes independent means 5 shapes x 5 fonts is 25 distinguishable identities rather
 * than 5.
 * <p>
 * Every value is a compile-time constant and the client only ever submits a preset
 * <em>name</em>. That is deliberate: these land in a {@code :root} block, and accepting
 * an arbitrary {@code box-shadow} string from an admin form would be a CSS-injection
 * sink that no realistic regex fully closes. A closed enum has no such surface.
 */
public enum ThemeStylePreset {

    ACADEMIC("Restrained",
            "Small radii, soft shadows — traditional and understated.",
            "3px", "5px", "8px",
            "0 1px 2px rgba(0, 0, 0, 0.22)",
            "0 1px 2px rgba(0, 0, 0, 0.30)",
            "0 3px 12px rgba(0, 0, 0, 0.30)",
            "0 6px 20px rgba(0, 0, 0, 0.50)"),

    MODERN("Soft",
            "Generous corners and diffuse shadows — product-like.",
            "8px", "12px", "18px",
            "0 2px 6px rgba(0, 0, 0, 0.18)",
            "0 1px 3px rgba(0, 0, 0, 0.24)",
            "0 6px 18px rgba(0, 0, 0, 0.28)",
            "0 12px 32px rgba(0, 0, 0, 0.44)"),

    CORPORATE("Crisp",
            "Tight radii, hard shadows — institutional and formal.",
            "2px", "3px", "4px",
            "0 1px 1px rgba(0, 0, 0, 0.28)",
            "0 1px 2px rgba(0, 0, 0, 0.34)",
            "0 2px 8px rgba(0, 0, 0, 0.32)",
            "0 4px 14px rgba(0, 0, 0, 0.46)"),

    MINIMAL("Flat",
            "Near-square corners, almost no elevation — quiet and plain.",
            "0px", "1px", "2px",
            "none",
            "none",
            "0 1px 4px rgba(0, 0, 0, 0.22)",
            "0 2px 10px rgba(0, 0, 0, 0.34)"),

    ROUNDED("Rounded",
            "Heavy rounding and lifted cards — friendly and approachable.",
            "12px", "18px", "26px",
            "0 2px 8px rgba(0, 0, 0, 0.16)",
            "0 1px 4px rgba(0, 0, 0, 0.20)",
            "0 8px 22px rgba(0, 0, 0, 0.26)",
            "0 16px 40px rgba(0, 0, 0, 0.40)");

    private final String label;
    private final String description;
    private final String radiusSm;
    private final String radius;
    private final String radiusLg;
    private final String shadowCard;
    private final String shadowSoft;
    private final String shadowHover;
    private final String shadowPop;

    ThemeStylePreset(String label, String description,
                     String radiusSm, String radius, String radiusLg,
                     String shadowCard, String shadowSoft, String shadowHover, String shadowPop) {
        this.label = label;
        this.description = description;
        this.radiusSm = radiusSm;
        this.radius = radius;
        this.radiusLg = radiusLg;
        this.shadowCard = shadowCard;
        this.shadowSoft = shadowSoft;
        this.shadowHover = shadowHover;
        this.shadowPop = shadowPop;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    /** The default when a deployment has never picked one — matches base.css. */
    public static ThemeStylePreset defaultPreset() {
        return ACADEMIC;
    }

    public static ThemeStylePreset fromNameOrDefault(String name) {
        if (name == null || name.isBlank()) {
            return defaultPreset();
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return defaultPreset();
        }
    }

    /**
     * Geometry and elevation only — typography comes from {@link ThemeFontPreset},
     * so the two axes can be mixed independently.
     */
    public String toCssDeclarations() {
        return """
                  --radius-sm: %s;
                  --radius: %s;
                  --radius-lg: %s;
                  --shadow-card: %s;
                  --shadow-soft: %s;
                  --shadow-hover: %s;
                  --shadow-pop: %s;
                """.formatted(radiusSm, radius, radiusLg,
                shadowCard, shadowSoft, shadowHover, shadowPop);
    }
}
