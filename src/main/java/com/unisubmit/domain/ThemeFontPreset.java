package com.unisubmit.domain;

/**
 * The typeface axis, kept separate from {@link ThemeStylePreset} so shape and type can be
 * combined freely — 5 fonts x 5 shapes is 25 distinguishable identities rather than 5.
 * <p>
 * Every stack ends in a generic family and leans on fonts that ship with the OS. The app
 * loads no webfonts, so naming something exotic would silently fall back and two schools
 * that picked different fonts would render identically.
 * <p>
 * As with {@link ThemeStylePreset}, the client submits only a NAME — these strings land
 * in a {@code :root} block, and {@code font-family} accepts quoted strings containing
 * almost anything, so it is not a value to accept from a form.
 */
public enum ThemeFontPreset {

    SERIF_DISPLAY("Serif headings",
            "Fraunces/Georgia headings over a sans body — the default academic pairing.",
            "'Fraunces', Georgia, 'Times New Roman', serif",
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', sans-serif"),

    ALL_SANS("Modern sans",
            "One geometric sans throughout — neutral and product-like.",
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', sans-serif"),

    CLASSICAL("Classical",
            "Serif throughout — formal, print-like, strongly traditional.",
            "Georgia, 'Times New Roman', Times, serif",
            "Georgia, Cambria, 'Times New Roman', Times, serif"),

    GROTESQUE("Grotesque",
            "Helvetica-family sans — tight, Swiss, corporate.",
            "'Helvetica Neue', Helvetica, Arial, sans-serif",
            "'Helvetica Neue', Helvetica, Arial, sans-serif"),

    TECHNICAL("Technical",
            "Monospaced headings over a sans body — engineering-department feel.",
            "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace",
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', sans-serif");

    private final String label;
    private final String description;
    private final String fontDisplay;
    private final String fontSans;

    ThemeFontPreset(String label, String description, String fontDisplay, String fontSans) {
        this.label = label;
        this.description = description;
        this.fontDisplay = fontDisplay;
        this.fontSans = fontSans;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    public static ThemeFontPreset defaultPreset() {
        return SERIF_DISPLAY;
    }

    public static ThemeFontPreset fromNameOrDefault(String name) {
        if (name == null || name.isBlank()) {
            return defaultPreset();
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return defaultPreset();
        }
    }

    public String toCssDeclarations() {
        return """
                  --font-display: %s;
                  --font-sans: %s;
                """.formatted(fontDisplay, fontSans);
    }
}
