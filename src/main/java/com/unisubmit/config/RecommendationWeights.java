package com.unisubmit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "unisubmit.recommendation.weight")
public class RecommendationWeights {
    private double keyword = 0.5;
    private double title = 0.3;
    private double unit = 0.2;
    private double semantic = 0.3;
    /** Weight of structured Technology-tag overlap (Phase 2/3 knowledge model). */
    private double technology = 0.35;
    /** Weight of structured ResearchArea-tag overlap (Phase 2/3 knowledge model). */
    private double researchArea = 0.25;

    /** Sum of all weights — used to normalise the final score back to 0..1. */
    public double totalWeight() {
        double total = keyword + title + unit + semantic + technology + researchArea;
        return total > 0 ? total : 1.0;
    }
}
