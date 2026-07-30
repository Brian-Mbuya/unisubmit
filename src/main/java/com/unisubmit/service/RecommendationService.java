package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.SubmissionSimilarity;
import com.unisubmit.domain.Technology;
import com.unisubmit.domain.User;
import com.unisubmit.dto.SimilarSubmission;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.repository.SubmissionSimilarityRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 5 — expanded recommendation engine.
 * <p>
 * Blends six signals into one weighted score (weights are configurable via
 * {@code unisubmit.recommendation.weight.*} in application.yml):
 * free-text keyword overlap, title word overlap, structural unit/department
 * proximity, SPECTER2 embedding cosine similarity, and the structured
 * Technology / ResearchArea tag overlap from the Phase 2 knowledge model.
 * The per-signal breakdown is persisted so the UI can explain every match.
 * <p>
 * Candidates are filtered against {@link SubmissionAccessService} visibility
 * rules both at precompute time and again at read time, so a student never
 * sees a match that exposes a submission they may not discover.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int CANDIDATE_LIMIT = 50;
    private static final int MAX_RESULTS = 5;

    private final SubmissionRepository submissionRepository;
    private final SubmissionSimilarityRepository similarityRepository;
    private final SubmissionAccessService accessService;
    private final com.unisubmit.config.RecommendationWeights weights;

    public RecommendationService(SubmissionRepository submissionRepository,
                                 SubmissionSimilarityRepository similarityRepository,
                                 SubmissionAccessService accessService,
                                 com.unisubmit.config.RecommendationWeights weights) {
        this.submissionRepository = submissionRepository;
        this.similarityRepository = similarityRepository;
        this.accessService = accessService;
        this.weights = weights;
    }

    /**
     * ID-based entry point for callers outside an open session (e.g. the
     * startup refresh runner) — reloads the submission inside this
     * transaction so lazy collections are initialisable.
     */
    @Transactional
    public void precomputeForSubmissionId(Long submissionId) {
        submissionRepository.findById(submissionId).ifPresent(this::precomputeForSubmission);
    }

    @Transactional
    public void precomputeForSubmission(Submission current) {
        log.debug("Recommendation compute triggered for submission {}", current.getId());

        AIInsight currentInsight = current.getAiInsight();
        List<String> currentKeywords = getKeywords(currentInsight);
        Map<String, String> currentTechs = tagNameMap(current.getTechnologies().stream()
                .map(Technology::getName).collect(Collectors.toList()));
        Map<String, String> currentAreas = tagNameMap(current.getResearchAreas().stream()
                .map(ResearchArea::getName).collect(Collectors.toList()));

        similarityRepository.deleteBySubmission(current);

        List<Submission> sameUnit = submissionRepository.findByCurriculum_UnitAndStudentNot(current.getCurriculum().getUnit(), current.getStudent());
        List<Submission> recentOthers = submissionRepository.findByStudentNotOrderByCreatedAtDesc(current.getStudent(), PageRequest.of(0, CANDIDATE_LIMIT));

        Set<Long> poolIds = new LinkedHashSet<>();
        for (Submission s : sameUnit) poolIds.add(s.getId());
        for (Submission s : recentOthers) poolIds.add(s.getId());
        poolIds.remove(current.getId());

        // One fetch-join query loads insight + tags for the whole pool,
        // replacing ~3 lazy-load queries per candidate.
        List<Submission> candidates = poolIds.isEmpty()
                ? List.of()
                : submissionRepository.findWithRecommendationDataByIdIn(poolIds);

        List<SubmissionSimilarity> newSims = new ArrayList<>();

        for (Submission candidate : candidates) {
            // Visibility: never score/store a candidate the owning student may
            // not discover (private drafts stay private).
            if (!accessService.canDiscoverSubmission(current.getStudent(), candidate)) {
                continue;
            }

            PairSignals signals = scorePair(current, currentKeywords, currentTechs, currentAreas,
                    candidate, weights);

            // Collaboration value comes from shared CONTENT — problem domains,
            // technologies, research areas, or a genuinely similar topic — NOT
            // from being in the same class. Same-unit alone is deliberately not
            // enough to surface a match; otherwise every student in a unit would
            // be recommended to every other, which defeats the point entirely.
            boolean meaningfulOverlap = !signals.sharedTechs().isEmpty()
                    || !signals.sharedAreas().isEmpty()
                    || !signals.sharedKeywords().isEmpty()
                    || signals.title() >= 0.30
                    || signals.semantic() >= 0.30;
            if (signals.identicalDocument() || meaningfulOverlap) {
                String reason = signals.identicalDocument()
                        ? "Identical document uploaded — possible duplicate submission"
                        : determineReason(signals.keyword(), signals.title(), signals.sameUnit(),
                                signals.sameCourse(), signals.sharedTechs(), signals.sharedAreas());

                SubmissionSimilarity sim = new SubmissionSimilarity();
                sim.setSubmissionA(current);
                sim.setSubmissionB(candidate);
                sim.setSimilarityScore(signals.finalScore());
                sim.setMatchedKeywords(signals.sharedKeywords());
                sim.setMatchedTechnologies(signals.sharedTechs());
                sim.setMatchedResearchAreas(signals.sharedAreas());
                sim.setKeywordScore(signals.keyword());
                sim.setTitleScore(signals.title());
                sim.setUnitScore(signals.unit());
                sim.setSemanticScore(signals.semantic());
                sim.setTechnologyScore(signals.technology());
                sim.setResearchAreaScore(signals.researchArea());
                sim.setSameUnit(signals.sameUnit());
                sim.setReason(reason);
                newSims.add(sim);
            }
        }

        similarityRepository.saveAll(newSims);
    }

    /** All six signals + derived final score for one submission pair. */
    public record PairSignals(double keyword, double title, double unit, double semantic,
                              double technology, double researchArea,
                              List<String> sharedKeywords, List<String> sharedTechs,
                              List<String> sharedAreas, boolean sameUnit, boolean sameCourse,
                              boolean identicalDocument, double finalScore) {}

    /**
     * Scores one candidate against the current submission using the GIVEN
     * weight set — the single source of truth for both the persisted
     * precompute and the evaluation harness's what-if rankings.
     */
    private PairSignals scorePair(Submission current, List<String> currentKeywords,
                                  Map<String, String> currentTechs, Map<String, String> currentAreas,
                                  Submission candidate, com.unisubmit.config.RecommendationWeights w) {
        AIInsight candidateInsight = candidate.getAiInsight();
        List<String> candidateKeywords = getKeywords(candidateInsight);

        List<String> sharedKeywords = intersection(currentKeywords, candidateKeywords);
        int maxKeywords = Math.max(Math.max(currentKeywords.size(), candidateKeywords.size()), 1);
        double keywordScore = (double) sharedKeywords.size() / maxKeywords;

        double titleScore = calculateTitleSimilarity(current.getTitle(), candidate.getTitle());
        boolean sameCourse = current.getCurriculum().getUnit().getDepartment().getId().equals(candidate.getCurriculum().getUnit().getDepartment().getId());
        boolean sameUnitFlag = current.getCurriculum().getUnit().getId().equals(candidate.getCurriculum().getUnit().getId());
        double unitScore = sameUnitFlag ? 1.0 : (sameCourse ? 0.5 : 0.0);
        double semanticScore = semanticSimilarity(current, candidate);

        // Structured knowledge-model overlap (Phase 2/3 tags, not free text)
        Map<String, String> candTechs = tagNameMap(candidate.getTechnologies().stream()
                .map(Technology::getName).collect(Collectors.toList()));
        Map<String, String> candAreas = tagNameMap(candidate.getResearchAreas().stream()
                .map(ResearchArea::getName).collect(Collectors.toList()));
        List<String> sharedTechs = sharedDisplayNames(currentTechs, candTechs);
        List<String> sharedAreas = sharedDisplayNames(currentAreas, candAreas);
        double technologyScore = jaccard(currentTechs.keySet(), candTechs.keySet());
        double researchAreaScore = jaccard(currentAreas.keySet(), candAreas.keySet());

        // Adaptive normalisation: a signal that CANNOT fire for this pair
        // (no embeddings stored, no extracted keywords) is excluded from the
        // denominator instead of silently dragging every score down.
        // Without this, two identical documents could never exceed ~84%
        // whenever the SPECTER sidecar is off.
        // Non-null AND equal-length — a dimension mismatch (e.g. an old SPECTER 768-d vector
        // beside a new 1536-d one) must not enter the score or throw downstream.
        boolean semanticEvaluable = current.getEmbedding() != null && candidate.getEmbedding() != null
                && current.getEmbedding().length == candidate.getEmbedding().length
                && current.getEmbedding().length > 0;
        boolean keywordEvaluable = !currentKeywords.isEmpty() && !candidateKeywords.isEmpty();
        double effectiveWeight = w.getTitle() + w.getUnit()
                + w.getTechnology() + w.getResearchArea()
                + (keywordEvaluable ? w.getKeyword() : 0.0)
                + (semanticEvaluable ? w.getSemantic() : 0.0);

        double weightedSum = (keywordScore * w.getKeyword())
                + (titleScore * w.getTitle())
                + (unitScore * w.getUnit())
                + (semanticScore * w.getSemantic())
                + (technologyScore * w.getTechnology())
                + (researchAreaScore * w.getResearchArea());
        double finalScore = effectiveWeight > 0 ? weightedSum / effectiveWeight : 0.0;

        // Integrity signal: byte-identical latest files are a duplicate
        // submission, not merely "similar work" — flag at full score.
        boolean identicalDocument = isIdenticalDocument(current, candidate);
        if (identicalDocument) {
            finalScore = 1.0;
        }

        return new PairSignals(keywordScore, titleScore, unitScore, semanticScore,
                technologyScore, researchAreaScore, sharedKeywords, sharedTechs, sharedAreas,
                sameUnitFlag, sameCourse, identicalDocument, finalScore);
    }

    /**
     * Live what-if ranking for the evaluation harness: scores the current
     * submission's candidate pool under an ARBITRARY weight configuration and
     * returns candidate submission IDs, best first. Nothing is persisted.
     */
    @Transactional
    public List<Long> rankCandidateIds(Submission current, com.unisubmit.config.RecommendationWeights whatIfWeights) {
        if (current.getCurriculum() == null || current.getCurriculum().getUnit() == null) {
            return List.of();
        }
        List<String> currentKeywords = getKeywords(current.getAiInsight());
        Map<String, String> currentTechs = tagNameMap(current.getTechnologies().stream()
                .map(Technology::getName).collect(Collectors.toList()));
        Map<String, String> currentAreas = tagNameMap(current.getResearchAreas().stream()
                .map(ResearchArea::getName).collect(Collectors.toList()));

        List<Submission> sameUnit = submissionRepository.findByCurriculum_UnitAndStudentNot(current.getCurriculum().getUnit(), current.getStudent());
        List<Submission> recentOthers = submissionRepository.findByStudentNotOrderByCreatedAtDesc(current.getStudent(), PageRequest.of(0, CANDIDATE_LIMIT));

        Set<Long> poolIds = new LinkedHashSet<>();
        for (Submission s : sameUnit) poolIds.add(s.getId());
        for (Submission s : recentOthers) poolIds.add(s.getId());
        poolIds.remove(current.getId());
        if (poolIds.isEmpty()) {
            return List.of();
        }

        record Ranked(Long id, double score) {}
        return submissionRepository.findWithRecommendationDataByIdIn(poolIds).stream()
                .filter(candidate -> accessService.canDiscoverSubmission(current.getStudent(), candidate))
                .map(candidate -> new Ranked(candidate.getId(),
                        scorePair(current, currentKeywords, currentTechs, currentAreas,
                                candidate, whatIfWeights).finalScore()))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed())
                .map(Ranked::id)
                .collect(Collectors.toList());
    }

    /**
     * Read-side lookup used by the submission detail page. The viewer is
     * re-checked against visibility rules at read time, because a candidate's
     * status may have changed (e.g. reverted to DRAFT) since precompute.
     */
    public List<SimilarSubmission> findSimilarSubmissions(Submission current, User viewer) {
        List<SubmissionSimilarity> metrics = similarityRepository.findBySubmissionOrderBySimilarityScoreDesc(current);

        return metrics.stream()
            .map(m -> {
                Submission target = m.getSubmissionA().getId().equals(current.getId()) ? m.getSubmissionB() : m.getSubmissionA();
                if (viewer != null && !accessService.canDiscoverSubmission(viewer, target)) {
                    return null;
                }
                String label;
                if (m.getSimilarityScore() >= 0.995) label = "Identical document";
                else if (m.getSimilarityScore() >= 0.45) label = "Strong match";
                else if (m.getSimilarityScore() >= 0.2) label = "Related work";
                else label = "Possible match";

                return new SimilarSubmission(
                        target,
                        label,
                        listOrEmpty(m.getMatchedKeywords()),
                        listOrEmpty(m.getMatchedTechnologies()),
                        listOrEmpty(m.getMatchedResearchAreas()),
                        m.getSimilarityScore(),
                        m.getReason(),
                        zeroIfNull(m.getKeywordScore()),
                        zeroIfNull(m.getTitleScore()),
                        zeroIfNull(m.getUnitScore()),
                        zeroIfNull(m.getSemanticScore()),
                        zeroIfNull(m.getTechnologyScore()),
                        zeroIfNull(m.getResearchAreaScore()),
                        Boolean.TRUE.equals(m.getSameUnit()));
            })
            .filter(Objects::nonNull)
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
    }

    /** Backwards-compatible overload: no viewer filtering (internal/admin use). */
    public List<SimilarSubmission> findSimilarSubmissions(Submission current) {
        return findSimilarSubmissions(current, null);
    }

    private String determineReason(double keywordScore, double titleScore, boolean sameUnit, boolean sameCourse,
                                   List<String> sharedTechs, List<String> sharedAreas) {
        if (!sharedAreas.isEmpty() && !sharedTechs.isEmpty())
            return "Shared research focus (" + sharedAreas.get(0) + ") and technology stack";
        if (!sharedAreas.isEmpty()) return "Same research area: " + String.join(", ", sharedAreas);
        if (sharedTechs.size() >= 2) return "Shared technology stack: " + String.join(", ", sharedTechs);
        if (keywordScore >= 0.4 && sameUnit) return "High keyword overlap within same unit";
        if (keywordScore >= 0.4 && sameCourse) return "High keyword overlap within same course";
        if (keywordScore >= 0.4) return "High keyword overlap across different domains";
        if (titleScore >= 0.5 && sameUnit) return "Similar topic within same unit";
        if (titleScore >= 0.5) return "Similar topic";
        if (!sharedTechs.isEmpty()) return "Shared technology: " + sharedTechs.get(0);
        // NOTE: no "same unit" fallback — a pair whose only link is the unit is
        // no longer retained as a match (see precomputeForSubmission), so we never
        // surface being classmates as a reason to collaborate.
        return "Related work in your field";
    }

    private double calculateTitleSimilarity(String t1, String t2) {
        if (t1 == null || t2 == null) return 0.0;
        Set<String> w1 = new HashSet<>(Arrays.asList(t1.toLowerCase().split("\\W+")));
        Set<String> w2 = new HashSet<>(Arrays.asList(t2.toLowerCase().split("\\W+")));
        w1.remove(""); w2.remove("");
        if (w1.isEmpty() && w2.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(w1);
        intersection.retainAll(w2);
        Set<String> union = new HashSet<>(w1);
        union.addAll(w2);
        return (double) intersection.size() / union.size();
    }

    /** Score at/above which two submissions are flagged as near-duplicates in the review queue. */
    public static final double NEAR_DUPLICATE_THRESHOLD = 0.85;

    /**
     * Which of the given submissions have a near-duplicate partner (score >= 0.85). One query
     * for the whole lecturer queue, so the dashboard stays N+1-free. Exception-labelling: the
     * caller renders a chip only for ids in this set, nothing otherwise.
     */
    public java.util.Set<Long> findNearDuplicateFlagged(java.util.Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(
                similarityRepository.findFlaggedSubmissionIds(submissionIds, NEAR_DUPLICATE_THRESHOLD));
    }

    private List<String> getKeywords(AIInsight insight) {
        if (insight == null || !insight.getStatus().hasContent()) return List.of();
        Set<String> kws = insight.getKeywords();
        return kws != null ? new ArrayList<>(kws) : List.of();
    }

    private List<String> intersection(List<String> a, List<String> b) {
        Set<String> bSet = new HashSet<>(b);
        return a.stream().filter(bSet::contains).collect(Collectors.toList());
    }

    /**
     * Normalised (lower-cased, trimmed) tag name → original display casing.
     * Overlap math runs on the lowercase keys; the UI gets the original values,
     * so "PostgreSQL" is never mangled into "Postgresql".
     */
    private static Map<String, String> tagNameMap(Collection<String> names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                map.putIfAbsent(n.trim().toLowerCase(), n.trim());
            }
        }
        return map;
    }

    /** Shared tags in their original display casing (current submission's wins). */
    private static List<String> sharedDisplayNames(Map<String, String> a, Map<String, String> b) {
        return a.keySet().stream()
                .filter(b::containsKey)
                .sorted()
                .map(a::get)
                .collect(Collectors.toList());
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static double zeroIfNull(Double d) {
        return d != null ? d : 0.0;
    }

    private static List<String> listOrEmpty(List<String> list) {
        return list != null ? list : List.of();
    }

    /** True when both submissions' latest uploaded files carry the same SHA-256. */
    private static boolean isIdenticalDocument(Submission a, Submission b) {
        String hashA = latestContentHash(a);
        String hashB = latestContentHash(b);
        return hashA != null && hashA.equals(hashB);
    }

    private static String latestContentHash(Submission s) {
        if (s.getVersions() == null || s.getVersions().isEmpty()) {
            return null;
        }
        return s.getVersions().get(s.getVersions().size() - 1).getContentHash();
    }

    private double semanticSimilarity(Submission a, Submission b) {
        if (a.getEmbedding() == null || b.getEmbedding() == null) {
            return 0.0;
        }
        float[] v1 = a.getEmbedding();
        float[] v2 = b.getEmbedding();
        if (v1.length != v2.length || v1.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
