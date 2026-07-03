package com.unisubmit.service;

import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.Technology;
import com.unisubmit.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Phase 7 — "the university's research landscape".
 * <p>
 * Clusters all analysed submissions and projects them onto a 2D map:
 * <ul>
 *   <li><b>Vectors</b>: SPECTER2 embeddings when present; otherwise a term
 *       one-hot vector over each submission's AI keywords + structured tags
 *       (works on the local H2 profile with no sidecar).</li>
 *   <li><b>Clustering</b>: plain-Java k-means (Lloyd's algorithm, deterministic
 *       seed) with k ≈ min(6, n/2).</li>
 *   <li><b>Projection</b>: PCA to two components via power iteration with
 *       deflation — no linear-algebra dependency needed.</li>
 * </ul>
 * The controller renders the result as an SVG dot map.
 */
@Service
public class AnalyticsService {

    public record LandscapePoint(Long submissionId, String title, String unitName,
                                 double x, double y, int cluster) {}

    public record LandscapeCluster(int index, int size, List<String> topTerms) {}

    public record Landscape(List<LandscapePoint> points, List<LandscapeCluster> clusters,
                            String vectorSource) {}

    private final SubmissionRepository submissionRepository;

    public AnalyticsService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public Landscape buildLandscape() {
        List<Submission> analysed = submissionRepository.findAll().stream()
                .filter(s -> s.getAiInsight() != null
                        && s.getAiInsight().getStatus() == AIInsightStatus.COMPLETED)
                .toList();
        if (analysed.size() < 2) {
            return new Landscape(List.of(), List.of(), "none");
        }

        // Prefer real embeddings when most of the corpus has them
        List<Submission> withEmbeddings = analysed.stream()
                .filter(s -> s.getEmbedding() != null && s.getEmbedding().length > 0)
                .toList();
        boolean useEmbeddings = withEmbeddings.size() >= Math.max(3, analysed.size() / 2);

        List<Submission> subjects = useEmbeddings ? withEmbeddings : analysed;
        double[][] vectors;
        List<String> vocabulary = new ArrayList<>();
        if (useEmbeddings) {
            vectors = new double[subjects.size()][];
            for (int i = 0; i < subjects.size(); i++) {
                float[] e = subjects.get(i).getEmbedding();
                vectors[i] = new double[e.length];
                for (int j = 0; j < e.length; j++) {
                    vectors[i][j] = e[j];
                }
            }
        } else {
            vectors = termVectors(subjects, vocabulary);
        }
        normalizeRows(vectors);

        int k = Math.max(2, Math.min(6, subjects.size() / 2));
        int[] assignment = kMeans(vectors, k);

        double[][] projected = pca2d(vectors);
        scaleToViewport(projected);

        List<LandscapePoint> points = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            Submission s = subjects.get(i);
            points.add(new LandscapePoint(s.getId(), s.getTitle(),
                    s.getUnit() != null ? s.getUnit().getUnitName() : "—",
                    projected[i][0], projected[i][1], assignment[i]));
        }

        List<LandscapeCluster> clusters = describeClusters(subjects, assignment, k);
        return new Landscape(points, clusters, useEmbeddings ? "SPECTER2 embeddings" : "keyword/tag vectors");
    }

    // ── Vector construction ──────────────────────────────────────────────────

    /** One-hot term vectors over keywords + structured tags; fills vocabulary. */
    private static double[][] termVectors(List<Submission> subjects, List<String> vocabulary) {
        Set<String> vocab = new LinkedHashSet<>();
        List<Set<String>> perDoc = new ArrayList<>();
        for (Submission s : subjects) {
            Set<String> terms = new LinkedHashSet<>();
            if (s.getAiInsight() != null && s.getAiInsight().getKeywords() != null) {
                s.getAiInsight().getKeywords().forEach(t -> terms.add(norm(t)));
            }
            s.getTechnologies().stream().map(Technology::getName).forEach(t -> terms.add(norm(t)));
            s.getResearchAreas().stream().map(ResearchArea::getName).forEach(t -> terms.add(norm(t)));
            terms.remove("");
            perDoc.add(terms);
            vocab.addAll(terms);
        }
        vocabulary.addAll(vocab);

        double[][] vectors = new double[subjects.size()][vocab.size()];
        int dim = 0;
        Map<String, Integer> index = new HashMap<>();
        for (String term : vocab) {
            index.put(term, dim++);
        }
        for (int i = 0; i < perDoc.size(); i++) {
            for (String term : perDoc.get(i)) {
                vectors[i][index.get(term)] = 1.0;
            }
        }
        return vectors;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static void normalizeRows(double[][] vectors) {
        for (double[] v : vectors) {
            double norm = 0;
            for (double x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int j = 0; j < v.length; j++) {
                    v[j] /= norm;
                }
            }
        }
    }

    // ── k-means (Lloyd, deterministic) ───────────────────────────────────────

    private static int[] kMeans(double[][] vectors, int k) {
        int n = vectors.length;
        int dim = vectors[0].length;
        Random random = new Random(42);

        // init: distinct random points as centroids
        double[][] centroids = new double[k][dim];
        Set<Integer> chosen = new LinkedHashSet<>();
        while (chosen.size() < k) {
            chosen.add(random.nextInt(n));
        }
        int c = 0;
        for (int idx : chosen) {
            centroids[c++] = vectors[idx].clone();
        }

        int[] assignment = new int[n];
        for (int iter = 0; iter < 30; iter++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double dist = squaredDistance(vectors[i], centroids[j]);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = j;
                    }
                }
                if (assignment[i] != best) {
                    assignment[i] = best;
                    moved = true;
                }
            }
            if (!moved && iter > 0) {
                break;
            }
            double[][] sums = new double[k][dim];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                counts[assignment[i]]++;
                for (int d = 0; d < dim; d++) {
                    sums[assignment[i]][d] += vectors[i][d];
                }
            }
            for (int j = 0; j < k; j++) {
                if (counts[j] > 0) {
                    for (int d = 0; d < dim; d++) {
                        centroids[j][d] = sums[j][d] / counts[j];
                    }
                }
            }
        }
        return assignment;
    }

    private static double squaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    // ── PCA via power iteration with deflation ───────────────────────────────

    private static double[][] pca2d(double[][] vectors) {
        int n = vectors.length;
        int dim = vectors[0].length;

        // centre the data
        double[] mean = new double[dim];
        for (double[] v : vectors) {
            for (int j = 0; j < dim; j++) {
                mean[j] += v[j];
            }
        }
        for (int j = 0; j < dim; j++) {
            mean[j] /= n;
        }
        double[][] centred = new double[n][dim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < dim; j++) {
                centred[i][j] = vectors[i][j] - mean[j];
            }
        }

        double[] pc1 = principalComponent(centred, null);
        double[] pc2 = principalComponent(centred, pc1);

        double[][] projected = new double[n][2];
        for (int i = 0; i < n; i++) {
            projected[i][0] = dot(centred[i], pc1);
            projected[i][1] = dot(centred[i], pc2);
        }
        return projected;
    }

    /** Power iteration on Xᵀ(Xv), deflating against an earlier component. */
    private static double[] principalComponent(double[][] centred, double[] deflateAgainst) {
        int dim = centred[0].length;
        Random random = new Random(7);
        double[] v = new double[dim];
        for (int j = 0; j < dim; j++) {
            v[j] = random.nextDouble() - 0.5;
        }
        for (int iter = 0; iter < 50; iter++) {
            if (deflateAgainst != null) {
                double proj = dot(v, deflateAgainst);
                for (int j = 0; j < dim; j++) {
                    v[j] -= proj * deflateAgainst[j];
                }
            }
            // w = Xᵀ (X v)
            double[] xv = new double[centred.length];
            for (int i = 0; i < centred.length; i++) {
                xv[i] = dot(centred[i], v);
            }
            double[] w = new double[dim];
            for (int i = 0; i < centred.length; i++) {
                for (int j = 0; j < dim; j++) {
                    w[j] += xv[i] * centred[i][j];
                }
            }
            double norm = Math.sqrt(dot(w, w));
            if (norm < 1e-12) {
                break;
            }
            for (int j = 0; j < dim; j++) {
                v[j] = w[j] / norm;
            }
        }
        if (deflateAgainst != null) {
            double proj = dot(v, deflateAgainst);
            for (int j = 0; j < dim; j++) {
                v[j] -= proj * deflateAgainst[j];
            }
        }
        return v;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** Scales projected coordinates into a 5–95 viewBox range with padding. */
    private static void scaleToViewport(double[][] projected) {
        for (int axis = 0; axis < 2; axis++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double[] p : projected) {
                min = Math.min(min, p[axis]);
                max = Math.max(max, p[axis]);
            }
            double range = max - min;
            for (double[] p : projected) {
                p[axis] = range < 1e-12 ? 50.0 : 5 + 90 * (p[axis] - min) / range;
            }
        }
    }

    // ── Cluster descriptions ─────────────────────────────────────────────────

    private static List<LandscapeCluster> describeClusters(List<Submission> subjects,
                                                           int[] assignment, int k) {
        List<LandscapeCluster> clusters = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            Map<String, Integer> termCounts = new LinkedHashMap<>();
            int size = 0;
            for (int i = 0; i < subjects.size(); i++) {
                if (assignment[i] != j) {
                    continue;
                }
                size++;
                Submission s = subjects.get(i);
                if (s.getAiInsight() != null && s.getAiInsight().getKeywords() != null) {
                    s.getAiInsight().getKeywords()
                            .forEach(t -> termCounts.merge(norm(t), 1, Integer::sum));
                }
                s.getResearchAreas().forEach(a -> termCounts.merge(norm(a.getName()), 2, Integer::sum));
                s.getTechnologies().forEach(t -> termCounts.merge(norm(t.getName()), 1, Integer::sum));
            }
            List<String> topTerms = termCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();
            if (size > 0) {
                clusters.add(new LandscapeCluster(j, size, topTerms));
            }
        }
        clusters.sort(Comparator.comparingInt(LandscapeCluster::size).reversed());
        return clusters;
    }
}
