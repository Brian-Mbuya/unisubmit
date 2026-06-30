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
    private final com.unisubmit.service.KnowledgeTagService tagService;
    private final TeachingAssignmentRepository teachingAssignmentRepository;

    public LecturerController(SubmissionService submissionService,
                              com.unisubmit.service.AnnouncementService announcementService,
                              com.unisubmit.service.KnowledgeTagService tagService,
                              TeachingAssignmentRepository teachingAssignmentRepository) {
        this.submissionService = submissionService;
        this.announcementService = announcementService;
        this.tagService = tagService;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
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
        model.addAttribute("allTechnologies", tagService.getAllTechnologies());
        model.addAttribute("allResearchAreas", tagService.getAllResearchAreas());
        model.addAttribute("allFrameworks", tagService.getAllFrameworks());
        model.addAttribute("allDatabases", tagService.getAllDatabases());
        model.addAttribute("allProgrammingLanguages", tagService.getAllProgrammingLanguages());
        model.addAttribute("allSkills", tagService.getAllSkills());
        return "lecturer/review-split";
    }

    @PostMapping("/submission/{id}/tags")
    public String updateTags(@PathVariable Long id,
                             @RequestParam(required = false) List<Long> technologyIds,
                             @RequestParam(required = false) List<Long> researchAreaIds,
                             @RequestParam(required = false) List<Long> frameworkIds,
                             @RequestParam(required = false) List<Long> databaseIds,
                             @RequestParam(required = false) List<Long> programmingLanguageIds,
                             @RequestParam(required = false) List<Long> skillIds) {
        tagService.updateSubmissionTags(id, technologyIds, researchAreaIds, frameworkIds, databaseIds, programmingLanguageIds, skillIds);
        return "redirect:/lecturer/submission/" + id + "?success=Tags updated";
    }

    @PostMapping("/submission/{id}/review")
    public String submitReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               @RequestParam(required = false) String message,
                               @RequestParam(required = false) SubmissionStatus status,
                               @RequestParam(required = false) Integer grade) {
        submissionService.addFeedbackAndReview(id, userDetails.getUser(), message, status, grade);
        // Redirect back to the submission so the lecturer can see the saved feedback immediately
        return "redirect:/lecturer/submission/" + id;
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        announcementService.deleteAnnouncement(id, userDetails.getUser());
        return "redirect:/lecturer/announcements?success=Notice removed and deadline reset";
    }
}
