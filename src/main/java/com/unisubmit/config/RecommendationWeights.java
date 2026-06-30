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
}
