package com.unisubmit.config;

import com.unisubmit.domain.Submission;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recomputes every submission's persisted similarity rows at startup, so
 * scoring changes (weights, adaptive normalisation) reach existing data
 * without waiting for the next AI analysis or tag edit.
 * <p>
 * Opt-in via {@code unisubmit.recommendation.refresh-on-startup=true}
 * (enabled in the local H2 profile; leave off in production where the
 * data volume may make startup recompute undesirable).
 */
@Component
@Order(20) // after seed data runners
@ConditionalOnProperty(name = "unisubmit.recommendation.refresh-on-startup", havingValue = "true")
public class RecommendationRefreshRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRefreshRunner.class);

    private final SubmissionRepository submissionRepository;
    private final RecommendationService recommendationService;

    public RecommendationRefreshRunner(SubmissionRepository submissionRepository,
                                       RecommendationService recommendationService) {
        this.submissionRepository = submissionRepository;
        this.recommendationService = recommendationService;
    }

    @Override
    public void run(String... args) {
        // IDs only — the entity is reloaded inside the service's transaction,
        // otherwise its lazy collections are unreachable from this runner.
        List<Long> ids = submissionRepository.findAll().stream()
                .map(Submission::getId)
                .toList();
        int refreshed = 0;
        for (Long id : ids) {
            try {
                recommendationService.precomputeForSubmissionId(id);
                refreshed++;
            } catch (Exception ex) {
                log.warn("Similarity refresh failed for submission {}: {}", id, ex.getMessage());
            }
        }
        log.info("Startup similarity refresh complete: {}/{} submissions recomputed", refreshed, ids.size());
    }
}
