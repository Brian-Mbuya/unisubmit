package com.unisubmit.controller;

import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.dto.CollaborationInboxView;
import com.unisubmit.dto.SimilarSubmission;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AcademicHierarchyService;
import com.unisubmit.service.CollaborationRequestService;
import com.unisubmit.service.RecommendationService;
import com.unisubmit.service.SubmissionService;
import com.unisubmit.service.UnitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final SubmissionService submissionService;
    private final UnitService unitService;
    private final RecommendationService recommendationService;
    private final com.unisubmit.service.LecturerRecommendationService lecturerRecommendationService;
    private final CollaborationRequestService collaborationRequestService;
    private final AcademicHierarchyService academicHierarchyService;
    private final com.unisubmit.service.ProjectGroupService groupService;
    private final com.unisubmit.service.AnnouncementService announcementService;
    private final com.unisubmit.service.AIInsightService aiInsightService;

    public StudentController(SubmissionService submissionService,
                             UnitService unitService,
                             RecommendationService recommendationService,
                             com.unisubmit.service.LecturerRecommendationService lecturerRecommendationService,
                             CollaborationRequestService collaborationRequestService,
                             AcademicHierarchyService academicHierarchyService,
                             com.unisubmit.service.ProjectGroupService groupService,
                             com.unisubmit.service.AnnouncementService announcementService,
                             com.unisubmit.service.AIInsightService aiInsightService) {
        this.submissionService = submissionService;
        this.unitService = unitService;
        this.recommendationService = recommendationService;
        this.lecturerRecommendationService = lecturerRecommendationService;
        this.collaborationRequestService = collaborationRequestService;
        this.academicHierarchyService = academicHierarchyService;
        this.groupService = groupService;
        this.announcementService = announcementService;
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("submissions", submissionService.getSubmissionsForStudent(userDetails.getUser()));
        return "student/dashboard";
    }

    @GetMapping("/announcements")
    public String viewAnnouncements(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        List<com.unisubmit.domain.Announcement> all = announcementService.getAnnouncementsForStudent(userDetails.getUser().getId());
        
        List<com.unisubmit.domain.Announcement> assignments = all.stream()
                .filter(a -> a.getType() == com.unisubmit.domain.AnnouncementType.ASSIGNMENT)
                .toList();
                
        List<com.unisubmit.domain.Announcement> generalAnnouncements = all.stream()
                .filter(a -> a.getType() == com.unisubmit.domain.AnnouncementType.ANNOUNCEMENT)
                .toList();

        // Latest notice (within last 48 h) to power the pop-up modal
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(48);
        List<com.unisubmit.domain.Announcement> latestNotice = all.stream()
                .filter(a -> a.getCreatedAt().isAfter(cutoff))
                .limit(1)
                .toList();
                
        model.addAttribute("assignments", assignments);
        model.addAttribute("generalAnnouncements", generalAnnouncements);
        model.addAttribute("latestNotice", latestNotice);
        return "student/announcements";
    }

    @GetMapping("/submission/new")
    public String newSubmissionForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @RequestParam(required = false) Long groupId,
                                    @RequestParam(required = false) Long unitId,
                                    Model model) {
        var student = userDetails.getUser();
        StudentProfile profile = student.getStudentProfile();

        if (profile != null && profile.getProgramme() != null && profile.getProgramme().getDepartment() != null) {
            Long deptId = profile.getProgramme().getDepartment().getId();
            model.addAttribute("studentDepartmentName", profile.getProgramme().getDepartment().getName());
            model.addAttribute("units", academicHierarchyService.findUnitsByDepartment(deptId));
        } else {
            model.addAttribute("studentDepartmentName", null);
            model.addAttribute("units", unitService.findAllUnits());
        }

        model.addAttribute("groups", groupService.findGroupsForUser(student));
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("selectedUnitId", unitId);

        return "student/new-submission";
    }

    @PostMapping("/submission")
    public String createSubmission(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @RequestParam(required = false) Long unitId,
                                   @RequestParam String title,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long groupId,
                                   RedirectAttributes redirectAttributes) {
        if (unitId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a unit before submitting.");
            return "redirect:/student/submission/new" + (groupId != null ? "?groupId=" + groupId : "");
        }
        try {
            submissionService.createSubmission(userDetails.getUser(), unitId, title, file, groupId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/submission/new" + (groupId != null ? "?groupId=" + groupId : "");
        }
        // One-shot flash that triggers the "submission received" success moment
        // on the dashboard (see student/dashboard.html).
        redirectAttributes.addFlashAttribute("submissionReceived", title);
        return "redirect:/student/dashboard";
    }

    @GetMapping("/submission/{id}")
    public String viewSubmission(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @PathVariable Long id, Model model) {
        Submission submission = submissionService.getSubmissionForStudent(id, userDetails.getUser());
        model.addAttribute("submission", submission);

        List<SimilarSubmission> similar =
                recommendationService.findSimilarSubmissions(submission, userDetails.getUser());

        model.addAttribute("similarSubmissions", similar);
        model.addAttribute("lecturerMatches",
                lecturerRecommendationService.recommendLecturersFor(submission));
        model.addAttribute("requestStatusesBySubmissionId",
                collaborationRequestService.getRequestStatusesForSender(
                        userDetails.getUser(),
                        similar.stream().map(SimilarSubmission::submission).toList()));
        return "student/submission-detail";
    }

    @PostMapping("/submission/{id}/version")
    public String addNewVersion(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String changesSummary) {
        submissionService.addNewVersion(userDetails.getUser(), id, file, changesSummary);
        return "redirect:/student/submission/" + id;
    }

    @PostMapping("/collaboration-requests")
    public String sendCollaborationRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                           @RequestParam Long submissionId,
                                           @RequestParam(required = false) String message,
                                           RedirectAttributes redirectAttributes) {
        try {
            collaborationRequestService.createRequest(userDetails.getUser(), submissionId, message);
            redirectAttributes.addFlashAttribute("successMessage", "Collaboration request sent.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/student/inbox";
    }

    @GetMapping("/inbox")
    public String collaborationInbox(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        CollaborationInboxView inbox = collaborationRequestService.getInbox(userDetails.getUser());
        model.addAttribute("inbox", inbox);
        return "student/inbox";
    }

    @PostMapping("/collaboration-requests/{id}/accept")
    public String acceptCollaborationRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @PathVariable Long id,
                                             RedirectAttributes redirectAttributes) {
        try {
            collaborationRequestService.acceptRequest(userDetails.getUser(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Collaboration request accepted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/student/inbox";
    }

    @PostMapping("/collaboration-requests/{id}/decline")
    public String declineCollaborationRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @PathVariable Long id,
                                              RedirectAttributes redirectAttributes) {
        try {
            collaborationRequestService.declineRequest(userDetails.getUser(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Collaboration request declined.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/student/inbox";
    }

    @PostMapping("/submission/{id}/retry-analysis")
    public String retrySubmissionAnalysis(@AuthenticationPrincipal CustomUserDetails userDetails,
                                          @PathVariable Long id,
                                          RedirectAttributes ra) {
        Submission submission = submissionService.getSubmissionForStudent(id, userDetails.getUser());
        if (submission != null) {
            aiInsightService.initiateAnalysis(submission);
            ra.addFlashAttribute("successMessage", "AI analysis has been re-queued using the active model.");
        } else {
            ra.addFlashAttribute("errorMessage", "Submission not found.");
        }
        return "redirect:/student/submission/" + id;
    }
}
