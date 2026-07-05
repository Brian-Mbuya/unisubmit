package com.unisubmit.config;

import com.unisubmit.service.AnnouncementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Phase 9 — fires assignment deadline reminders hourly. The actual window logic
 * and dedup live in {@link AnnouncementService#sendDeadlineReminders()}.
 */
@Component
public class DeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);

    private final AnnouncementService announcementService;

    public DeadlineReminderScheduler(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** Hourly, plus once ~30s after startup so the feature is testable immediately. */
    @Scheduled(initialDelay = 30_000, fixedRate = 3_600_000)
    public void run() {
        try {
            int sent = announcementService.sendDeadlineReminders();
            if (sent > 0) {
                log.info("Deadline reminders sent: {}", sent);
            }
        } catch (Exception ex) {
            log.warn("Deadline reminder run failed: {}", ex.getMessage());
        }
    }
}
