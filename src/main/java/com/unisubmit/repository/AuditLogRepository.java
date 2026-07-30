package com.unisubmit.repository;

import com.unisubmit.domain.AuditLog;
import com.unisubmit.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findBySubmissionOrderByCreatedAtAscIdAsc(Submission submission);
}
