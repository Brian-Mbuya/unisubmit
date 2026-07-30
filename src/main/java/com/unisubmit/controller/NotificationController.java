package com.unisubmit.controller;

import com.unisubmit.domain.AppNotification;
import com.unisubmit.domain.Role;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Lists all notifications for the current user (newest first). */
    @GetMapping
    public String listNotifications(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        List<AppNotification> notifications =
                notificationService.getNotificationsForUser(userDetails.getUser());
        model.addAttribute("notifications", notifications);
        return "notifications";
    }

    /**
     * Opens the page that actually contains the announced submission, chosen by the
     * viewer's role: students land on their submission detail, lecturers on the review
     * page, admins on the project view. (A lecturer not assigned to the unit will still
     * hit getSubmissionForLecturer's clear 403 message — acceptable, see roadmap 2.4.)
     */
    @GetMapping("/open/{submissionId}")
    public String openNotification(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @PathVariable Long submissionId) {
        Role role = userDetails.getUser().getRole();
        if (role == Role.STUDENT) {
            return "redirect:/student/submission/" + submissionId;
        }
        if (role == Role.LECTURER) {
            return "redirect:/lecturer/submission/" + submissionId;
        }
        return "redirect:/projects/" + submissionId;
    }

    /** Marks all unread notifications as read, then redirects back. */
    @PostMapping("/mark-read")
    public String markAllRead(@AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        notificationService.markAllRead(userDetails.getUser());
        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }
}
