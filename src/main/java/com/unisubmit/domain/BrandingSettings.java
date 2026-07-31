package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branding_settings")
public class BrandingSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** JSON payload containing mapped CSS custom property tokens */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String tokensJson;

    /**
     * The institution this deployment belongs to, shown in the navbar and page titles.
     * Nullable: deployments branded before this column existed have none, and the UI
     * falls back to "UniSubmit".
     */
    @Column(name = "school_name", length = 120)
    private String schoolName;

    /**
     * The institution's logo as a {@code data:image/png;base64,...} URI.
     * <p>
     * Deliberately PNG-only and rasterised in the browser before upload, even when the
     * admin picked an SVG. An SVG can carry {@code <script>} and would become stored XSS
     * the moment it were rendered inline; a canvas re-encode drops everything that is not
     * pixels. The server still never parses the image — it only validates the envelope
     * (see {@code BrandingService.validateLogo}).
     */
    @Column(name = "logo_png", columnDefinition = "TEXT")
    private String logoPng;

    @Column(nullable = false)
    private Instant updatedAt;
}
