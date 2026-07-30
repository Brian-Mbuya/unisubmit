package com.unisubmit.service;

import com.unisubmit.config.RecommendationWeights;
import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CollaborationRequestRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 7 — evaluation harness for the recommendation engine.
 * <p>
 * Ground truth: every ACCEPTED collaboration request proves that the target
 * submission was genuinely relevant to the requesting student. For each such
 * pair the harness replays the recommender under several weight
 * configurations and measures where the accepted target actually ranked:
 * <ul>
 *   <li><b>precision@5</b> — fraction of ground-truth targets inside the top 5</li>
 *   <li><b>MRR</b> — mean reciprocal rank (1/rank, 0 when never retrieved)</li>
 * </ul>
 * A pair's rank is the BEST rank across all of the requester's own
 * submissions, since the request may have originated from any of them.
 * Nothing is persisted — rankings are computed live per configuration.
 */
@Service
public class EvaluationService {

    /** Rank cut-off for the precision metric. */
    static final int PRECISION_CUTOFF = 5;

    public record ConfigResult(String name, String weightsSummary, int pairs,
                               int retrieved, double precisionAtK, double mrr) {}

    public record EvaluationReport(int groundTruthPairs, List<ConfigResult> results) {}

    /** Phase 8 — collaboration engine health: shortlist volume + connect acceptance. */
    public record CollaborationStats(long highValueMatches, long mediumValueMatches,
                                     long shortlistedPairs, long connectRequests,
                                     long accepted, long declined, long pending,
                                     double acceptanceRate) {}

    private final CollaborationRequestRepository requestRepository;
    private final SubmissionRepository submissionRepository;
    private final RecommendationService recommendationService;
    private final RecommendationWeights currentWeights;
    private final com.unisubmit.repository.CollaborationMatchRepository matchRepository;

    public EvaluationService(CollaborationRequestRepository requestRepository,
                             SubmissionRepository submissionRepository,
                             RecommendationService recommendationService,
                             RecommendationWeights currentWeights,
                             com.unisubmit.repository.CollaborationMatchRepository matchRepository) {
        this.requestRepository = requestRepository;
        this.submissionRepository = submissionRepository;
        this.recommendationService = recommendationService;
        this.currentWeights = currentWeights;
        this.matchRepository = matchRepository;
    }

    /**
     * Ground truth for the collaboration engine: how often "Request to connect"
     * gets accepted. Because Discover reuses the collaboration-request flow, this
     * is the acceptance rate over all connect/collaborate requests — exactly the
     * signal for tuning the collaboration weights.
     */
    @Transactional(readOnly = true)
    public CollaborationStats collaborationStats() {
        long high = matchRepository.countByCollaborationValueIn(
                List.of(com.unisubmit.domain.CollaborationValue.HIGH));
        long medium = matchRepository.countByCollaborationValueIn(
                List.of(com.unisubmit.domain.CollaborationValue.MEDIUM));
        long shortlisted = matchRepository.count();

        long accepted = requestRepository.findByStatus(CollaborationRequestStatus.ACCEPTED).size();
        long declined = requestRepository.findByStatus(CollaborationRequestStatus.DECLINED).size();
        long pending = requestRepository.findByStatus(CollaborationRequestStatus.PENDING).size();
        long decided = accepted + declined;
        double rate = decided == 0 ? 0.0 : (double) accepted / decided;

        return new CollaborationStats(high, medium, shortlisted,
                accepted + declined + pending, accepted, declined, pending, rate);
    }

    /** Acceptance rate split by WHY Stage-1 suggested the pair (B6c telemetry). */
    public record ReasonStat(String reason, long accepted, long declined, double acceptanceRate) {}

    /**
     * Does the complementarity engine actually produce better suggestions than plain overlap?
     * This is the measurement: acceptance rate per reason category. A reason with no decided
     * requests yet is still listed (rate 0) so the row doesn't silently vanish.
     */
    @Transactional(readOnly = true)
    public List<ReasonStat> acceptanceByReason() {
        java.util.Map<com.unisubmit.domain.MatchReasonType, long[]> tally =
                new java.util.EnumMap<>(com.unisubmit.domain.MatchReasonType.class);
        for (com.unisubmit.domain.MatchReasonType t : com.unisubmit.domain.MatchReasonType.values()) {
            tally.put(t, new long[]{0, 0}); // [accepted, declined]
        }

        List<com.unisubmit.domain.CollaborationRequest> decided = new ArrayList<>();
        decided.addAll(requestRepository.findByStatus(CollaborationRequestStatus.ACCEPTED));
        decided.addAll(requestRepository.findByStatus(CollaborationRequestStatus.DECLINED));

        for (com.unisubmit.domain.CollaborationRequest r : decided) {
            if (r.getSubmission() == null || r.getSender() == null) {
                continue;
            }
            com.unisubmit.domain.MatchReasonType type = reasonTypeFor(r.getSubmission(), r.getSender());
            if (type == null) {
                continue; // request wasn't driven by a stored match (e.g. from the similar-work rail)
            }
            boolean accepted = r.getStatus() == CollaborationRequestStatus.ACCEPTED;
            tally.get(type)[accepted ? 0 : 1]++;
        }

        List<ReasonStat> out = new ArrayList<>();
        for (var e : tally.entrySet()) {
            long accepted = e.getValue()[0];
            long declined = e.getValue()[1];
            long total = accepted + declined;
            out.add(new ReasonStat(e.getKey().label(), accepted, declined,
                    total == 0 ? 0.0 : (double) accepted / total));
        }
        return out;
    }

    /** The reason on the match linking this target submission to the requester's own work. */
    private com.unisubmit.domain.MatchReasonType reasonTypeFor(
            com.unisubmit.domain.Submission target, User sender) {
        for (com.unisubmit.domain.CollaborationMatch m : matchRepository.findBySubmission(target)) {
            com.unisubmit.domain.Submission other =
                    m.getSubmissionA() != null && m.getSubmissionA().getId().equals(target.getId())
                            ? m.getSubmissionB() : m.getSubmissionA();
            if (other != null && other.getStudent() != null
                    && other.getStudent().getId().equals(sender.getId())) {
                return m.getReasonType();
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public EvaluationReport evaluate() {
        // Ground truth: (requesting student, accepted target submission)
        record GroundTruth(User requester, Long targetSubmissionId) {}
        List<GroundTruth> truths = requestRepository.findByStatus(CollaborationRequestStatus.ACCEPTED).stream()
                .filter(r -> r.getSubmission() != null && r.getSender() != null)
                .map(r -> new GroundTruth(r.getSender(), r.getSubmission().getId()))
                .distinct()
                .toList();

        List<ConfigResult> results = new ArrayList<>();
        for (Map.Entry<String, RecommendationWeights> config : configurations().entrySet()) {
            RecommendationWeights w = config.getValue();
            int retrieved = 0;
            int hitsAtK = 0;
            double reciprocalSum = 0.0;

            for (GroundTruth truth : truths) {
                int bestRank = Integer.MAX_VALUE;
                for (Submission own : submissionRepository.findByStudent(truth.requester())) {
                    if (own.getId().equals(truth.targetSubmissionId())) {
                        continue;
                    }
                    List<Long> ranked = recommendationService.rankCandidateIds(own, w);
                    int rank = ranked.indexOf(truth.targetSubmissionId());
                    if (rank >= 0) {
                        bestRank = Math.min(bestRank, rank + 1);
                    }
                }
                if (bestRank != Integer.MAX_VALUE) {
                    retrieved++;
                    reciprocalSum += 1.0 / bestRank;
                    if (bestRank <= PRECISION_CUTOFF) {
                        hitsAtK++;
                    }
                }
            }

            int pairs = truths.size();
            results.add(new ConfigResult(
                    config.getKey(),
                    summarize(w),
                    pairs,
                    retrieved,
                    pairs == 0 ? 0.0 : (double) hitsAtK / pairs,
                    pairs == 0 ? 0.0 : reciprocalSum / pairs));
        }
        return new EvaluationReport(truths.size(), results);
    }

    /**
     * The weight configurations under comparison. First entry is whatever is
     * live in application.yml, the rest are ablations that show what each
     * signal family contributes — exactly why the weights were made
     * configurable in Phase 5.
     */
    private Map<String, RecommendationWeights> configurations() {
        Map<String, RecommendationWeights> configs = new LinkedHashMap<>();
        configs.put("Current (application.yml)", currentWeights);
        configs.put("Keywords only", weights(1.0, 0, 0, 0, 0, 0));
        configs.put("Structured tags only", weights(0, 0, 0, 0, 0.6, 0.4));
        configs.put("Title + unit only", weights(0, 0.6, 0.4, 0, 0, 0));
        configs.put("Uniform blend", weights(1, 1, 1, 1, 1, 1));
        configs.put("No structural bias (unit=0)", weights(0.5, 0.3, 0, 0.3, 0.35, 0.25));
        return configs;
    }

    private static RecommendationWeights weights(double keyword, double title, double unit,
                                                 double semantic, double technology, double researchArea) {
        RecommendationWeights w = new RecommendationWeights();
        w.setKeyword(keyword);
        w.setTitle(title);
        w.setUnit(unit);
        w.setSemantic(semantic);
        w.setTechnology(technology);
        w.setResearchArea(researchArea);
        return w;
    }

    private static String summarize(RecommendationWeights w) {
        return String.format(Locale.ROOT, "kw %.2f · title %.2f · unit %.2f · sem %.2f · tech %.2f · area %.2f",
                w.getKeyword(), w.getTitle(), w.getUnit(), w.getSemantic(), w.getTechnology(), w.getResearchArea());
    }
}
