package com.unisubmit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 8 — weights for the collaboration mechanical pre-filter (Stage 1),
 * configurable under {@code unisubmit.collaboration.weight.*}.
 * <p>
 * Deliberately the OPPOSITE emphasis of the similarity engine: there is no
 * unit/title weight at all (same class ≠ collaboration), and instead semantic
 * meaning, shared technologies, shared research areas and shared PROBLEM DOMAIN
 * carry the score, with explicit bonuses for cross-department and
 * mentorship-shaped (more mature / senior) pairings.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "unisubmit.collaboration.weight")
public class CollaborationWeights {
    /** SPECTER embedding cosine — the strongest cross-disciplinary signal. */
    private double semantic = 0.4;
    private double technology = 0.2;
    private double researchArea = 0.25;
    /** Broad problem-domain overlap (transportation, healthcare, …) — the bridge signal. */
    private double problemDomain = 0.35;
    /** Flat bonus added when the two submissions come from different departments. */
    private double crossDepartmentBonus = 0.15;
    /** Flat bonus when the candidate looks like a mentor (completed/senior work). */
    private double mentorshipBonus = 0.1;
    /**
     * Flat bonus for a COMPLEMENT pair: same problem space, disjoint methods. Deliberately
     * the largest bonus — "same problem, different toolkit" is what this engine exists to
     * surface, and it must outrank same-method twins.
     */
    private double complement = 0.25;

    public double signalWeightTotal() {
        double total = semantic + technology + researchArea + problemDomain;
        return total > 0 ? total : 1.0;
    }
}
