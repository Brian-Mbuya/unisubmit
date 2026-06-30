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
        return collaborationRequestService.getInbox(userDetails.getUser()).pendingIncomingCount();
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return 0L;
        }
        return notificationService.getUnreadCount(userDetails.getUser());
    }
}

