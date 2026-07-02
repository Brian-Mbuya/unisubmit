package com.unisubmit.service;

import com.unisubmit.domain.Feedback;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.Technology;
import com.unisubmit.domain.User;
import com.unisubmit.dto.LecturerMatch;
import com.unisubmit.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Phase 5 — lecturer matching.
 * Recommends lecturers whose past reviewed submissions share technologies and
 * research areas with the current submission. Pure SQL/Java aggregation over
 * the feedback history — "no LLM, just math".
 */
@Service
public class LecturerRecommendationService {

    private static final int MAX_RESULTS = 3;

    private final FeedbackRepository feedbackRepository;

    public LecturerRecommendationService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public List<LecturerMatch> recommendLecturersFor(Submission current) {
        Set<String> currentTechs = lowerNames(current.getTechnologies().stream()
                .map(Technology::getName).collect(Collectors.toSet()));
        Set<String> currentAreas = lowerNames(current.getResearchAreas().stream()
                .map(ResearchArea::getName).collect(Collectors.toSet()));

        if (currentTechs.isEmpty() && currentAreas.isEmpty()) {
            return List.of();
        }

        // Aggregate each lecturer's review history: which submissions they
        // reviewed, and the union of tags across those submissions.
        Map<Long, LecturerAggregate> byLecturer = new HashMap<>();
        for (Feedback feedback : feedbackRepository.findAllWithReviewedSubmissions()) {
            Submission reviewed = feedback.getSubmissionVersion().getSubmission();
            if (reviewed.getId().equals(current.getId())) {
                continue; // reviewing the current submission itself proves nothing
            }
            User lecturer = feedback.getLecturer();
            LecturerAggregate agg = byLecturer.computeIfAbsent(lecturer.getId(),
                    id -> new LecturerAggregate(lecturer));
            agg.reviewedSubmissionIds.add(reviewed.getId());
            for (Technology t : reviewed.getTechnologies()) {
                agg.reviewedTechs.add(t.getName());
            }
            for (ResearchArea r : reviewed.getResearchAreas()) {
                agg.reviewedAreas.add(r.getName());
            }
        }

        Long currentDeptId = current.getCurriculum() != null
                && current.getCurriculum().getUnit() != null
                && current.getCurriculum().getUnit().getDepartment() != null
                ? current.getCurriculum().getUnit().getDepartment().getId() : null;

        List<LecturerMatch> matches = new ArrayList<>();
        for (LecturerAggregate agg : byLecturer.values()) {
            List<String> sharedTechs = intersectPreservingCase(agg.reviewedTechs, currentTechs);
            List<String> sharedAreas = intersectPreservingCase(agg.reviewedAreas, currentAreas);
            int overlap = sharedTechs.size() + sharedAreas.size();
            if (overlap == 0) {
                continue;
            }

            boolean sameDept = currentDeptId != null
                    && agg.lecturer.getLecturerProfile() != null
                    && agg.lecturer.getLecturerProfile().getDepartment() != null
                    && currentDeptId.equals(agg.lecturer.getLecturerProfile().getDepartment().getId());

            // Research-area alignment is weighted above individual technologies;
            // experience volume is a mild tie-breaker, same department a small bonus.
            double score = sharedTechs.size()
                    + (sharedAreas.size() * 1.5)
                    + Math.min(agg.reviewedSubmissionIds.size(), 10) * 0.05
                    + (sameDept ? 0.5 : 0.0);

            matches.add(new LecturerMatch(agg.lecturer, sharedTechs, sharedAreas,
                    agg.reviewedSubmissionIds.size(), overlap, sameDept, score));
        }

        return matches.stream()
                .sorted(Comparator.comparingDouble(LecturerMatch::score).reversed())
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    private static Set<String> lowerNames(Set<String> names) {
        return names.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    /** Case-insensitive intersection that keeps the lecturer-side display casing. */
    private static List<String> intersectPreservingCase(Set<String> displayNames, Set<String> lowerTargets) {
        Set<String> seen = new HashSet<>();
        List<String> shared = new ArrayList<>();
        for (String name : new TreeSet<>(displayNames)) {
            String key = name.trim().toLowerCase();
            if (lowerTargets.contains(key) && seen.add(key)) {
                shared.add(name);
            }
        }
        return shared;
    }

    private static final class LecturerAggregate {
        final User lecturer;
        final Set<Long> reviewedSubmissionIds = new HashSet<>();
        final Set<String> reviewedTechs = new HashSet<>();
        final Set<String> reviewedAreas = new HashSet<>();

        LecturerAggregate(User lecturer) {
            this.lecturer = lecturer;
        }
    }
}
