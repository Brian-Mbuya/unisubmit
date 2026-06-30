package com.unisubmit.service;

import com.unisubmit.domain.Submission;
import com.unisubmit.dto.CollaboratorDTO;
import com.unisubmit.dto.SimilarSubmission;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollaboratorService {

    private static final int MAX_COLLABORATORS = 3;

    private final RecommendationService recommendationService;

    public CollaboratorService(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    public List<CollaboratorDTO> findCollaborators(Submission current) {
        List<SimilarSubmission> similar = recommendationService.findSimilarSubmissions(current);

        Map<String, StudentAccumulator> accumulators = new LinkedHashMap<>();

        for (SimilarSubmission sim : similar) {
            if (sim.sharedKeywords().isEmpty()) continue;

            Submission candidateSub = sim.submission();
            String studentId = candidateSub.getStudent().getStudentProfile() != null 
                ? candidateSub.getStudent().getStudentProfile().getAdmissionNumber() 
                : null;
            if (studentId == null) studentId = candidateSub.getStudent().getUsername();

            accumulators.computeIfAbsent(studentId, id -> new StudentAccumulator(
                    candidateSub.getStudent().getName(), id))
                    .addKeywords(sim.sharedKeywords());
        }

        return accumulators.values().stream()
                .sorted(Comparator.comparingInt(StudentAccumulator::totalKeywords).reversed())
                .limit(MAX_COLLABORATORS)
                .map(acc -> new CollaboratorDTO(
                        acc.name,
                        acc.studentId,
                        new ArrayList<>(acc.keywords),
                        acc.totalKeywords()))
                .collect(Collectors.toList());
    }

    private static class StudentAccumulator {
        final String name;
        final String studentId;
        final Set<String> keywords = new LinkedHashSet<>();

        StudentAccumulator(String name, String studentId) {
            this.name = name;
            this.studentId = studentId;
        }

        void addKeywords(List<String> kws) {
            keywords.addAll(kws);
        }

        int totalKeywords() {
            return keywords.size();
        }
    }
}
