package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionSimilarity;
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

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int CANDIDATE_LIMIT = 50;
    private static final int MAX_RESULTS = 5;

    private final SubmissionRepository submissionRepository;
    private final SubmissionSimilarityRepository similarityRepository;
    private final com.unisubmit.config.RecommendationWeights weights;

    public RecommendationService(SubmissionRepository submissionRepository, 
                                 SubmissionSimilarityRepository similarityRepository,
                                 com.unisubmit.config.RecommendationWeights weights) {
        this.submissionRepository = submissionRepository;
        this.similarityRepository = similarityRepository;
        this.weights = weights;
    }

    @Transactional
    public void precomputeForSubmission(Submission current) {
        log.debug("Recommendation compute triggered for submission {}", current.getId());
        
        AIInsight currentInsight = current.getAiInsight();
        List<String> currentKeywords = getKeywords(currentInsight); 

        similarityRepository.deleteBySubmission(current);

        List<Submission> sameUnit = submissionRepository.findByCurriculum_UnitAndStudentNot(current.getCurriculum().getUnit(), current.getStudent());
        List<Submission> recentOthers = submissionRepository.findByStudentNotOrderByCreatedAtDesc(current.getStudent(), PageRequest.of(0, CANDIDATE_LIMIT));

        Map<Long, Submission> pool = new LinkedHashMap<>();
        for (Submission s : sameUnit) pool.put(s.getId(), s);
        for (Submission s : recentOthers) pool.put(s.getId(), s);
        pool.remove(current.getId());

        List<SubmissionSimilarity> newSims = new ArrayList<>();

        for (Submission candidate : pool.values()) {
            AIInsight candidateInsight = candidate.getAiInsight();
            List<String> candidateKeywords = getKeywords(candidateInsight);
            
            List<String> sharedKeywords = intersection(currentKeywords, candidateKeywords);
            int maxKeywords = Math.max(Math.max(currentKeywords.size(), candidateKeywords.size()), 1);
            double keywordScore = (double) sharedKeywords.size() / maxKeywords;

            double titleScore = calculateTitleSimilarity(current.getTitle(), candidate.getTitle());
            boolean sameCourse = current.getCurriculum().getUnit().getDepartment().getId().equals(candidate.getCurriculum().getUnit().getDepartment().getId()); // approximate since courseName is gone
            boolean sameUnitFlag = current.getCurriculum().getUnit().getId().equals(candidate.getCurriculum().getUnit().getId());

            double unitScore = sameUnitFlag ? 1.0 : (sameCourse ? 0.5 : 0.0);

            double finalScore = (keywordScore * weights.getKeyword()) 
                              + (titleScore * weights.getTitle()) 
                              + (unitScore * weights.getUnit())
                              + (semanticSimilarity(current, candidate) * weights.getSemantic());

            if (finalScore >= 0.1 || sameUnitFlag) {
                String reason = determineReason(keywordScore, titleScore, sameUnitFlag, sameCourse);
                
                SubmissionSimilarity sim = new SubmissionSimilarity();
                sim.setSubmissionA(current);
                sim.setSubmissionB(candidate);
                sim.setSimilarityScore(finalScore);
                sim.setMatchedKeywords(sharedKeywords);
                sim.setReason(reason);
                newSims.add(sim);
            }
        }
        
        similarityRepository.saveAll(newSims);
    }

    public List<SimilarSubmission> findSimilarSubmissions(Submission current) {
        List<SubmissionSimilarity> metrics = similarityRepository.findBySubmissionOrderBySimilarityScoreDesc(current);
        
        return metrics.stream()
            .map(m -> {
                Submission target = m.getSubmissionA().getId().equals(current.getId()) ? m.getSubmissionB() : m.getSubmissionA();
                String label;
                if (m.getSimilarityScore() >= 0.6) label = "Strong Match";
                else if (m.getSimilarityScore() >= 0.3) label = "Related Work";
                else label = "Possible Match";
                
                return new SimilarSubmission(target, label, m.getMatchedKeywords(), m.getSimilarityScore(), m.getReason());
            })
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
    }

    private String determineReason(double keywordScore, double titleScore, boolean sameUnit, boolean sameCourse) {
        if (keywordScore >= 0.4 && sameUnit) return "High keyword overlap within same unit";
        if (keywordScore >= 0.4 && sameCourse) return "High keyword overlap within same course";
        if (keywordScore >= 0.4) return "High keyword overlap across different domains";
        if (titleScore >= 0.5 && sameUnit) return "Similar topic within same unit";
        if (titleScore >= 0.5) return "Similar topic";
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
        List<String> kws = insight.getKeywords();
        return kws != null ? kws : List.of();
    }

    private List<String> intersection(List<String> a, List<String> b) {
        Set<String> bSet = new HashSet<>(b);
        return a.stream().filter(bSet::contains).collect(Collectors.toList());
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
