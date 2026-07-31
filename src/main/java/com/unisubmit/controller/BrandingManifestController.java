package com.unisubmit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unisubmit.service.BrandingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the PWA manifest with the school's own identity.
 * <p>
 * This was the last place the platform's branding leaked: the manifest was a static file
 * naming "UniSubmit" and pointing at {@code /icons/*}, so installing a fully rebranded
 * deployment to a home screen still produced a UniSubmit icon and title. A controller
 * mapping takes precedence over the static resource handler, so this replaces the file
 * without needing to delete it — unbranded deployments get byte-equivalent defaults.
 */
@RestController
public class BrandingManifestController {

    private final BrandingService brandingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrandingManifestController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<String> manifest() throws Exception {
        String schoolName = brandingService.getSchoolName();
        String logo = brandingService.getLogoDataUri();
        String canvas = brandingService.getCanvasColor();
        String name = schoolName != null ? schoolName : "UniSubmit";

        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", name);
        // Home-screen labels get truncated around 12 characters; a long university name
        // would render as an ellipsis, so fall back to the first word.
        root.put("short_name", name.length() <= 12 ? name : name.split("\\s+")[0]);
        root.put("description",
                "Submit coursework, track feedback, and find collaborators across your university.");
        root.put("id", "/");
        root.put("start_url", "/");
        root.put("scope", "/");
        root.put("display", "standalone");
        root.put("orientation", "portrait-primary");
        root.put("background_color", canvas);
        root.put("theme_color", canvas);
        root.put("lang", "en");

        ArrayNode categories = root.putArray("categories");
        categories.add("education");
        categories.add("productivity");

        ArrayNode icons = root.putArray("icons");
        if (logo != null) {
            // A data: URI is a valid manifest icon src. Using one avoids adding a public
            // binary endpoint just to serve a logo the pages already inline.
            // "any maskable" because the stored PNG is square and padded by the canvas
            // rasterisation step, so it survives Android's mask crop.
            icons.add(iconNode(logo, "256x256", "image/png", "any maskable"));
        } else {
            icons.add(iconNode("/icons/icon.svg", "any", "image/svg+xml", "any"));
            icons.add(iconNode("/icons/icon-192.png", "192x192", "image/png", "any"));
            icons.add(iconNode("/icons/icon-512.png", "512x512", "image/png", "any"));
            icons.add(iconNode("/icons/icon-maskable-192.png", "192x192", "image/png", "maskable"));
            icons.add(iconNode("/icons/icon-maskable-512.png", "512x512", "image/png", "maskable"));
        }

        ArrayNode shortcuts = root.putArray("shortcuts");
        shortcuts.add(shortcutNode("New submission", "/student/submission/new"));
        shortcuts.add(shortcutNode("Notifications", "/notifications"));
        shortcuts.add(shortcutNode("Explore the archive", "/explore"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                // The manifest now varies with branding, so it must not be cached as long
                // as the static file it replaced.
                .header("Cache-Control", "no-cache")
                .body(objectMapper.writeValueAsString(root));
    }

    private ObjectNode iconNode(String src, String sizes, String type, String purpose) {
        ObjectNode icon = objectMapper.createObjectNode();
        icon.put("src", src);
        icon.put("sizes", sizes);
        icon.put("type", type);
        icon.put("purpose", purpose);
        return icon;
    }

    private ObjectNode shortcutNode(String name, String url) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("url", url);
        return node;
    }
}
