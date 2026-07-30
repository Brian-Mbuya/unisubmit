package com.unisubmit.service;

import com.unisubmit.domain.AuditAction;
import com.unisubmit.domain.AuditLog;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes and reads the append-only submission activity history.
 * Phase 4 — Academic Memory.
 * <p>
 * Only {@link #record} is exposed for writes — there is deliberately no update
 * or delete operation, so a submission's timeline cannot be rewritten after the
 * fact.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Appends a single immutable event to the submission's history. */
    @Transactional
    public void record(Submission submission, AuditAction action, String detail, User actor) {
        AuditLog entry = new AuditLog();
        entry.setSubmission(submission);
        entry.setAction(action);
        entry.setDetail(detail);
        entry.setActor(actor);
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getHistory(Submission submission) {
        return auditLogRepository.findBySubmissionOrderByCreatedAtAscIdAsc(submission);
    }
}
