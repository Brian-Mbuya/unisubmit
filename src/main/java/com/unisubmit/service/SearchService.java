package com.unisubmit.service;

import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.Technology;
import com.unisubmit.domain.User;
import com.unisubmit.repository.SubmissionRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 7 — hybrid project search.
 * <p>
 * Two retrieval channels fused with Reciprocal Rank Fusion (score =
 * Σ 1/(60+rank)):
 * <ol>
 *   <li><b>BM25 keyword ranking</b> (plain Java, works on any database) over
 *       title, AI summary, extracted keywords and structured tags — this is
 *       the "BM25 keyword upgrade" of roadmap item 5 applied where it belongs,
 *       to retrieval;</li>
 *   <li><b>semantic KNN</b> via SPECTER query embedding + the pgvector
 *       {@code <=>} cosine operator — PostgreSQL-only, gated behind
 *       {@code unisubmit.search.semantic-enabled} and degrading silently to
 *       keyword-only anywhere else (e.g. the local H2 profile).</li>
 * </ol>
 * Every hit is re-checked against {@link SubmissionAccessService} for the
 * viewer, so search can never surface a submission the viewer may not see.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final int MAX_RESULTS = 20;
    private static final int RRF_K = 60;
    private static final double BM25_K1 = 1.5;
    private static final double BM25_B = 0.75;

    public record SearchHit(Submission submission, double score, String snippet, boolean semanticMatch) {}

    private final SubmissionRepository submissionRepository;
    private final SubmissionAccessService accessService;
    private final SpecterService specterService;
    private final EntityManager entityManager;

    @Value("${unisubmit.search.semantic-enabled:false}")
    private boolean semanticEnabled;

    public SearchService(SubmissionRepository submissionRepository,
                         SubmissionAccessService accessService,
                         SpecterService specterService,
                         EntityManager entityManager) {
        this.submissionRepository = submissionRepository;
        this.accessService = accessService;
        this.specterService = specterService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(String query, User viewer) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        // Visibility first: the corpus only ever contains what the viewer may see.
        List<Submission> corpus = submissionRepository.findAll().stream()
                .filter(s -> accessService.canDiscoverSubmission(viewer, s))
                .toList();
        if (corpus.isEmpty()) {
            return List.of();
        }

        List<Long> keywordRanking = bm25Ranking(queryTerms, corpus);
        List<Long> semanticRanking = semanticRanking(query, corpus);

        // Reciprocal Rank Fusion over however many channels produced results
        Map<Long, Double> fused = new LinkedHashMap<>();
        for (List<Long> ranking : List.of(keywordRanking, semanticRanking)) {
            for (int rank = 0; rank < ranking.size(); rank++) {
                fused.merge(ranking.get(rank), 1.0 / (RRF_K + rank + 1), Double::sum);
            }
        }
        if (fused.isEmpty()) {
            return List.of();
        }

        Map<Long, Submission> byId = new HashMap<>();
        corpus.forEach(s -> byId.put(s.getId(), s));

        return fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_RESULTS)
                .map(e -> new SearchHit(byId.get(e.getKey()), e.getValue(),
                        snippet(byId.get(e.getKey())), semanticRanking.contains(e.getKey())))
                .filter(hit -> hit.submission() != null)
                .toList();
    }

    public boolean isSemanticEnabled() {
        return semanticEnabled && specterService != null;
    }

    // ── Channel 1: BM25 ──────────────────────────────────────────────────────

    private List<Long> bm25Ranking(List<String> queryTerms, List<Submission> corpus) {
        // Build a token bag per document; the title is weighted by inclusion twice.
        Map<Long, List<String>> docTokens = new LinkedHashMap<>();
        for (Submission s : corpus) {
            List<String> tokens = new ArrayList<>();
            tokens.addAll(tokenize(s.getTitle()));
            tokens.addAll(tokenize(s.getTitle()));
            if (s.getAiInsight() != null && s.getAiInsight().getStatus() == AIInsightStatus.COMPLETED) {
                tokens.addAll(tokenize(s.getAiInsight().getSummary()));
                s.getAiInsight().getKeywords().forEach(k -> tokens.addAll(tokenize(k)));
            }
            s.getTechnologies().stream().map(Technology::getName).forEach(n -> tokens.addAll(tokenize(n)));
            s.getResearchAreas().stream().map(ResearchArea::getName).forEach(n -> tokens.addAll(tokenize(n)));
            docTokens.put(s.getId(), tokens);
        }

        double avgLength = docTokens.values().stream().mapToInt(List::size).average().orElse(1.0);
        int totalDocs = docTokens.size();

        // Document frequency per query term
        Map<String, Integer> df = new HashMap<>();
        for (String term : queryTerms) {
            int count = 0;
            for (List<String> tokens : docTokens.values()) {
                if (tokens.contains(term)) {
                    count++;
                }
            }
            df.put(term, count);
        }

        record Scored(Long id, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<Long, List<String>> doc : docTokens.entrySet()) {
            List<String> tokens = doc.getValue();
            double score = 0.0;
            for (String term : queryTerms) {
                int termDf = df.getOrDefault(term, 0);
                if (termDf == 0) {
                    continue;
                }
                long tf = tokens.stream().filter(term::equals).count();
                if (tf == 0) {
                    continue;
                }
                double idf = Math.log(1.0 + (totalDocs - termDf + 0.5) / (termDf + 0.5));
                double norm = (tf * (BM25_K1 + 1))
                        / (tf + BM25_K1 * (1 - BM25_B + BM25_B * tokens.size() / avgLength));
                score += idf * norm;
            }
            if (score > 0) {
                scored.add(new Scored(doc.getKey(), score));
            }
        }
        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .map(Scored::id)
                .toList();
    }

    // ── Channel 2: pgvector semantic KNN (optional) ──────────────────────────

    @SuppressWarnings("unchecked")
    private List<Long> semanticRanking(String query, List<Submission> corpus) {
        if (!semanticEnabled) {
            return List.of();
        }
        try {
            Optional<float[]> embedded = specterService.embed(query);
            if (embedded.isEmpty()) {
                return List.of();
            }
            String vectorLiteral = toVectorLiteral(embedded.get());
            // PostgreSQL + pgvector only; corpus membership re-filters the raw
            // ids so visibility rules still hold.
            List<Object> rows = entityManager.createNativeQuery(
                            "SELECT id FROM submissions WHERE embedding IS NOT NULL "
                                    + "ORDER BY embedding <=> CAST(:v AS vector) LIMIT 20")
                    .setParameter("v", vectorLiteral)
                    .getResultList();
            List<Long> visible = corpus.stream().map(Submission::getId).toList();
            return rows.stream()
                    .map(o -> ((Number) o).longValue())
                    .filter(visible::contains)
                    .toList();
        } catch (Exception ex) {
            // H2, missing extension, sidecar down — keyword channel carries the query.
            log.debug("Semantic search unavailable, degrading to keyword-only: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+"))
                .filter(t -> t.length() >= 2)
                .toList();
    }

    private static String snippet(Submission s) {
        if (s == null) {
            return "";
        }
        if (s.getAiInsight() != null && s.getAiInsight().getStatus() == AIInsightStatus.COMPLETED
                && s.getAiInsight().getSummary() != null) {
            String summary = s.getAiInsight().getSummary();
            return summary.length() > 220 ? summary.substring(0, 220) + "…" : summary;
        }
        return "No AI summary yet.";
    }
}
