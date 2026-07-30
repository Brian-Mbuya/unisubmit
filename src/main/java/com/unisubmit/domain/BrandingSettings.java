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

    @Column(nullable = false)
    private Instant updatedAt;
}
