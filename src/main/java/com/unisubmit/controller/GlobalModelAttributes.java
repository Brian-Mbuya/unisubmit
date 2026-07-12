package com.unisubmit.controller;

import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.CollaborationRequestService;
import com.unisubmit.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final CollaborationRequestService collaborationRequestService;
    private final NotificationService notificationService;

    public GlobalModelAttributes(CollaborationRequestService collaborationRequestService,
                                 NotificationService notificationService) {
        this.collaborationRequestService = collaborationRequestService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("pendingCollaborationCount")
    public long pendingCollaborationCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null || !userDetails.getUser().getRole().name().equals("STUDENT")) {
            return 0L;
        }
        return collaborationRequestService.countPendingIncoming(userDetails.getUser());
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return 0L;
        }
        return notificationService.getUnreadCount(userDetails.getUser());
    }

    /** Time-of-day greeting, e.g. "Good morning". */
    @ModelAttribute("greeting")
    public String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    /**
     * The signed-in user's name for a personal greeting. Names that start with
     * an honorific keep it with the surname ("Dr. Smith"), otherwise the first
     * name is used ("John") — a bare first word would greet "Dr." on its own.
     */
    @ModelAttribute("currentFirstName")
    public String currentFirstName(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null || userDetails.getUser().getName() == null) {
            return "there";
        }
        String name = userDetails.getUser().getName().trim();
        String[] words = name.split("\\s+");
        if (words.length >= 2 && words[0].matches("(?i)(dr|prof|mr|mrs|ms|miss|eng|rev)\\.?")) {
            return words[0] + " " + words[1];
        }
        return words[0];
    }
}

