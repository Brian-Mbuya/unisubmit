package com.unisubmit.service;

import com.unisubmit.domain.AppNotification;
import com.unisubmit.domain.NotificationType;
import com.unisubmit.domain.User;
import com.unisubmit.repository.AppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final AppNotificationRepository notificationRepository;

    public NotificationService(AppNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates and persists a new in-app notification.
     *
     * @param recipient         the user who will receive the notification
     * @param type              the notification category
     * @param message           human-readable message text
     * @param relatedSubmissionId optional submission context (may be null)
     */
    @Transactional
    public AppNotification createNotification(User recipient,
                                              NotificationType type,
                                              String message,
                                              Long relatedSubmissionId) {
        AppNotification notification = new AppNotification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRelatedSubmissionId(relatedSubmissionId);
        return notificationRepository.save(notification);
    }

    /** Returns unread notification count for a user (used by the bell badge). */
    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    /** Returns all notifications for a user, newest first. */
    public List<AppNotification> getNotificationsForUser(User user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
    }

    /** Marks all unread notifications for a user as read. */
    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllReadForRecipient(user);
    }
}
