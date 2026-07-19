package com.unisubmit.controller;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.User;
import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.repository.TeachingAssignmentRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.SubmissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {

    private final SubmissionService submissionService;
    private final com.unisubmit.service.AnnouncementService announcementService;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final com.unisubmit.service.RecommendationService recommendationService;

    /** Phase 7 — blind review: hide student identity until a grade is given. */
    @org.springframework.beans.factory.annotation.Value("${unisubmit.review.blind-mode:false}")
    private boolean blindReviewMode;

    public LecturerController(SubmissionService submissionService,
                              com.unisubmit.service.AnnouncementService announcementService,
                              TeachingAssignmentRepository teachingAssignmentRepository,
                              com.unisubmit.service.RecommendationService recommendationService) {
        this.submissionService = submissionService;
        this.announcementService = announcementService;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/announcements")
    public String announcements(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User lecturer = userDetails.getUser();
        if (lecturer.getLecturerProfile() != null) {
            List<TeachingAssignment> assignments = teachingAssignmentRepository.findByLecturerId(lecturer.getLecturerProfile().getId());
            List<Unit> units = assignments.stream()
                    .map(TeachingAssignment::getCurriculum)
                    .map(com.unisubmit.domain.Curriculum::getUnit)
                    .distinct()
                    .collect(Collectors.toList());
            model.addAttribute("units", units);
        } else {
            model.addAttribute("units", List.of());
        }
        model.addAttribute("announcements", announcementService.getAnnouncementsByLecturer(lecturer.getId()));
        return "lecturer/announcements";
    }

    @PostMapping("/announcements")
    public String createAnnouncement(@RequestParam Long unitId,
                                     @RequestParam String title,
                                     @RequestParam String message,
                                     @RequestParam(required = false) com.unisubmit.domain.AnnouncementType type,
                                     @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime deadline,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        User lecturer = userDetails.getUser();
        com.unisubmit.domain.AnnouncementType finalType = (type != null) ? type : com.unisubmit.domain.AnnouncementType.ANNOUNCEMENT;
        // A deadline only makes sense on an assignment — trust the date over the dropdown.
        if (deadline != null) {
            finalType = com.unisubmit.domain.AnnouncementType.ASSIGNMENT;
        }
        announcementService.createAnnouncement(lecturer, unitId, title, message, finalType, deadline);
        return "redirect:/lecturer/announcements?success=Announcement posted";
    }


    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @RequestParam(required = false) String statusFilter,
                            @RequestParam(required = false) String typeFilter,
                            Model model) {
        User lecturer = userDetails.getUser();
        List<Submission> submissions = submissionService.getSubmissionsForLecturer(lecturer);

        List<Submission> filtered = submissions;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                SubmissionStatus filterStatus = SubmissionStatus.valueOf(statusFilter);
                filtered = submissions.stream()
                        .filter(s -> s.getStatus() == filterStatus)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (typeFilter != null && !typeFilter.isBlank()) {
            if ("GROUP".equalsIgnoreCase(typeFilter)) {
                filtered = filtered.stream()
                        .filter(s -> s.getProjectGroup() != null)
                        .collect(Collectors.toList());
            } else if ("INDIVIDUAL".equalsIgnoreCase(typeFilter)) {
                filtered = filtered.stream()
                        .filter(s -> s.getProjectGroup() == null)
                        .collect(Collectors.toList());
            }
        }

        Map<Unit, List<Submission>> submissionsByUnit = filtered.stream()
                .collect(Collectors.groupingBy(s -> s.getCurriculum().getUnit(), LinkedHashMap::new, Collectors.toList()));

        long pendingReviews = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED
                        || s.getStatus() == SubmissionStatus.UNDER_REVIEW)
                .count();
        long completedReviews = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED
                        || s.getStatus() == SubmissionStatus.REJECTED)
                .count();

        model.addAttribute("submissions", filtered);
        model.addAttribute("submissionsByUnit", submissionsByUnit);
        model.addAttribute("pendingReviews", pendingReviews);
        model.addAttribute("completedReviews", completedReviews);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("typeFilter", typeFilter);

        return "lecturer/dashboard";
    }

    @GetMapping("/submission/{id}")
    public String viewSubmission(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @PathVariable Long id, Model model) {
        Submission submission = submissionService.getSubmissionForLecturer(id, userDetails.getUser());
        model.addAttribute("submission", submission);
        // Lecturer-side transparency: same similar-work panel the student sees,
        // read-only, so overlapping submissions surface during review.
        model.addAttribute("similarSubmissions",
                recommendationService.findSimilarSubmissions(submission, userDetails.getUser()));
        // Blind review holds until the first graded feedback exists.
        boolean graded = submission.getVersions().stream()
                .flatMap(v -> v.getFeedbacks().stream())
                .anyMatch(f -> f.getGrade() != null);
        model.addAttribute("blindReview", blindReviewMode && !graded);
        return "lecturer/review-split";
    }

    /** Phase 9 — export a unit's marks as CSV (one row per submission). */
    @GetMapping("/units/{unitId}/marks.csv")
    public org.springframework.http.ResponseEntity<String> exportMarks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long unitId) {
        User lecturer = userDetails.getUser();
        List<Submission> subs = submissionService.getSubmissionsForLecturer(lecturer).stream()
                .filter(s -> s.getUnit() != null && s.getUnit().getId().equals(unitId))
                .collect(Collectors.toList());

        StringBuilder csv = new StringBuilder("Student ID,Name,Title,Status,Grade,Graded by,Graded on\n");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (Submission s : subs) {
            com.unisubmit.domain.Feedback latestGraded = s.getVersions().stream()
                    .flatMap(v -> v.getFeedbacks().stream())
                    .filter(f -> f.getGrade() != null)
                    .reduce((a, b) -> b.getTimestamp().isAfter(a.getTimestamp()) ? b : a)
                    .orElse(null);
            boolean graded = latestGraded != null;
            // Blind mode: withhold identity for still-ungraded work.
            boolean mask = blindReviewMode && !graded;
            String admission = s.getStudent().getStudentProfile() != null
                    ? s.getStudent().getStudentProfile().getAdmissionNumber() : "";
            csv.append(csv(mask ? "" : admission)).append(',')
               .append(csv(mask ? "Anonymous" : s.getStudent().getName())).append(',')
               .append(csv(s.getTitle())).append(',')
               .append(csv(s.getStatus().name())).append(',')
               .append(graded ? latestGraded.getGrade() : "").append(',')
               .append(csv(graded ? latestGraded.getLecturer().getName() : "")).append(',')
               .append(graded ? latestGraded.getTimestamp().format(fmt) : "").append('\n');
        }
        String filename = "marks-unit-" + unitId + ".csv";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    /** Delegates to the shared {@link com.unisubmit.util.CsvUtil#escape} escaper. */
    private static String csv(String value) {
        return com.unisubmit.util.CsvUtil.escape(value);
    }

    @PostMapping("/submission/{id}/review")
    public String submitReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               @RequestParam(required = false) String message,
                               @RequestParam(required = false) SubmissionStatus status,
                               @RequestParam(required = false) Integer grade) {
        submissionService.addFeedbackAndReview(id, userDetails.getUser(), message, status, grade);
        // Redirect back to the submission so the lecturer can see the saved feedback immediately
        return "redirect:/lecturer/submission/" + id + "?success=Review submitted successfully";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        announcementService.deleteAnnouncement(id, userDetails.getUser());
        return "redirect:/lecturer/announcements?success=Notice removed and deadline reset";
    }

    @PostMapping("/announcements/{id}/toggle-late-window")
    public String toggleLateWindow(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isNowOpen = announcementService.toggleLateWindow(id, userDetails.getUser());
        String msg = isNowOpen ? "Late submission window is now OPEN" : "Late submission window is now CLOSED";
        return "redirect:/lecturer/announcements?success=" + msg;
    }
}
