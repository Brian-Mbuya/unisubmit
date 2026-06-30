package com.unisubmit.repository;

import com.unisubmit.domain.AppNotification;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    /** All notifications for a user, newest first */
    List<AppNotification> findByRecipientOrderByCreatedAtDesc(User recipient);

    /** Count of unread notifications */
    long countByRecipientAndReadFalse(User recipient);

    /** All unread notifications for a user (used for mark-all-read) */
    List<AppNotification> findByRecipientAndReadFalse(User recipient);

    /** Bulk-mark all notifications for a user as read */
    @Modifying
    @Query("UPDATE AppNotification n SET n.read = true WHERE n.recipient = :recipient AND n.read = false")
    void markAllReadForRecipient(User recipient);
}
