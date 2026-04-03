package com.chuka.irir.repository;

import com.chuka.irir.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AuditLog} entities.
 *
 * Supports paginated queries for the admin system logs dashboard.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Find audit logs for a specific user, paginated. */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    /** Find audit logs by action type. */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /** Find all audit logs within a date range, paginated. */
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** Find all audit logs, paginated and ordered by most recent first. */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
