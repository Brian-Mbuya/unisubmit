package com.unisubmit.config;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionVersion;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.repository.SubmissionVersionRepository;
import com.unisubmit.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
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
    private final SubmissionVersionRepository versionRepository;
    private final RecommendationService recommendationService;
    private final com.unisubmit.service.CollaborationDiscoveryService collaborationDiscoveryService;
    private final com.unisubmit.service.CollaborationAssessmentService collaborationAssessmentService;
    private final Path uploadRoot;

    public RecommendationRefreshRunner(SubmissionRepository submissionRepository,
                                       SubmissionVersionRepository versionRepository,
                                       RecommendationService recommendationService,
                                       com.unisubmit.service.CollaborationDiscoveryService collaborationDiscoveryService,
                                       com.unisubmit.service.CollaborationAssessmentService collaborationAssessmentService,
                                       @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.recommendationService = recommendationService;
        this.collaborationDiscoveryService = collaborationDiscoveryService;
        this.collaborationAssessmentService = collaborationAssessmentService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        backfillContentHashes();
        // IDs only — the entity is reloaded inside the service's transaction,
        // otherwise its lazy collections are unreachable from this runner.
        List<Long> ids = submissionRepository.findAll().stream()
                .map(Submission::getId)
                .toList();
        int refreshed = 0;
        for (Long id : ids) {
            try {
                recommendationService.precomputeForSubmissionId(id);
                // Phase 8 Stage 1 — rebuild the cross-disciplinary collaboration shortlist.
                collaborationDiscoveryService.precomputeForSubmissionId(id);
                refreshed++;
            } catch (Exception ex) {
                log.warn("Similarity/collaboration refresh failed for submission {}: {}", id, ex.getMessage());
            }
        }
        // Phase 8 Stage 2 — assess the freshly-built shortlists (no-op without an API key).
        for (Long id : ids) {
            try {
                collaborationAssessmentService.assessForSubmission(id);
            } catch (Exception ex) {
                log.warn("Collaboration assessment trigger failed for submission {}: {}", id, ex.getMessage());
            }
        }
        log.info("Startup similarity + collaboration refresh complete: {}/{} submissions recomputed", refreshed, ids.size());
    }

    /**
     * Fingerprints versions uploaded before the content-hash column existed,
     * so identical-document detection also covers historical files.
     */
    private void backfillContentHashes() {
        int hashed = 0;
        for (SubmissionVersion version : versionRepository.findAll()) {
            if (version.getContentHash() != null) {
                continue;
            }
            Path file = uploadRoot.resolve(version.getFilePath());
            if (!Files.exists(file)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
                StringBuilder sb = new StringBuilder(64);
                for (byte b : digest.digest()) {
                    sb.append(String.format("%02x", b));
                }
                version.setContentHash(sb.toString());
                versionRepository.save(version);
                hashed++;
            } catch (Exception ex) {
                log.warn("Could not hash file for version {}: {}", version.getId(), ex.getMessage());
            }
        }
        if (hashed > 0) {
            log.info("Backfilled content hashes for {} existing file versions", hashed);
        }
    }
}
