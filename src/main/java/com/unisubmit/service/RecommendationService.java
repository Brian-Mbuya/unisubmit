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
            boolean semanticEvaluable = current.getEmbedding() != null && candidate.getEmbedding() != null;
            boolean keywordEvaluable = !currentKeywords.isEmpty() && !candidateKeywords.isEmpty();
            double effectiveWeight = weights.getTitle() + weights.getUnit()
                    + weights.getTechnology() + weights.getResearchArea()
                    + (keywordEvaluable ? weights.getKeyword() : 0.0)
                    + (semanticEvaluable ? weights.getSemantic() : 0.0);

            double weightedSum = (keywordScore * weights.getKeyword())
                    + (titleScore * weights.getTitle())
                    + (unitScore * weights.getUnit())
                    + (semanticScore * weights.getSemantic())
                    + (technologyScore * weights.getTechnology())
                    + (researchAreaScore * weights.getResearchArea());
            double finalScore = effectiveWeight > 0 ? weightedSum / effectiveWeight : 0.0;

            // Integrity signal: byte-identical latest files are a duplicate
            // submission, not merely "similar work" — flag at full score.
            boolean identicalDocument = isIdenticalDocument(current, candidate);
            if (identicalDocument) {
                finalScore = 1.0;
            }

            if (finalScore >= 0.05 || sameUnitFlag || !sharedTechs.isEmpty()) {
                String reason = identicalDocument
                        ? "Identical document uploaded — possible duplicate submission"
                        : determineReason(keywordScore, titleScore, sameUnitFlag, sameCourse,
                                sharedTechs, sharedAreas);

                SubmissionSimilarity sim = new SubmissionSimilarity();
                sim.setSubmissionA(current);
                sim.setSubmissionB(candidate);
                sim.setSimilarityScore(finalScore);
                sim.setMatchedKeywords(sharedKeywords);
                sim.setMatchedTechnologies(sharedTechs);
                sim.setMatchedResearchAreas(sharedAreas);
                sim.setKeywordScore(keywordScore);
                sim.setTitleScore(titleScore);
                sim.setUnitScore(unitScore);
                sim.setSemanticScore(semanticScore);
                sim.setTechnologyScore(technologyScore);
                sim.setResearchAreaScore(researchAreaScore);
                sim.setSameUnit(sameUnitFlag);
                sim.setReason(reason);
                newSims.add(sim);
            }
        }

        similarityRepository.saveAll(newSims);
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
        if (sameUnit) return "Same structural domain (Unit)";
        return "Related content concepts";
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

    private List<String> getKeywords(AIInsight insight) {
        if (insight == null || insight.getStatus() != AIInsightStatus.COMPLETED) return List.of();
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
