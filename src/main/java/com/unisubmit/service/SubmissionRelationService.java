package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.exception.SubmissionNotFoundException;
import com.unisubmit.repository.SubmissionRelationRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages curated lineage links ({@link SubmissionRelation}) between submissions.
 * Phase 4 — Academic Memory. Creation is restricted to lecturers and admins at
 * the controller layer; this service additionally enforces validation and a
 * simple per-user rate limit to prevent spam-linking.
 */
@Service
public class SubmissionRelationService {

    /** Max relations a single user may create within the rolling window. */
    private static final int MAX_RELATIONS_PER_WINDOW = 20;
    private static final java.time.Duration RATE_WINDOW = java.time.Duration.ofMinutes(1);

    private final SubmissionRelationRepository relationRepository;
    private final SubmissionRepository submissionRepository;
    private final AuditService auditService;

    public SubmissionRelationService(SubmissionRelationRepository relationRepository,
                                     SubmissionRepository submissionRepository,
                                     AuditService auditService) {
        this.relationRepository = relationRepository;
        this.submissionRepository = submissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SubmissionRelation> getRelationsFor(Submission submission) {
        return relationRepository.findBySubmission(submission);
    }

    /**
     * Creates a lineage link from {@code sourceId} to {@code targetId}.
     *
     * @throws IllegalArgumentException on invalid input or duplicate links
     * @throws IllegalStateException    when the creator exceeds the rate limit
     */
    @Transactional
    public SubmissionRelation createRelation(User creator, Long sourceId, Long targetId, RelationType type) {
        if (sourceId == null || targetId == null || type == null) {
            throw new IllegalArgumentException("Source, target and relation type are all required.");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("A project cannot be linked to itself.");
        }

        // Rate limit — guards against spam-linking even by authorised staff.
        if (creator != null) {
            long recent = relationRepository.countByCreatedByAndCreatedAtAfter(
                    creator, LocalDateTime.now().minus(RATE_WINDOW));
            if (recent >= MAX_RELATIONS_PER_WINDOW) {
                throw new IllegalStateException(
                        "Too many links created recently. Please wait a moment and try again.");
            }
        }

        Submission source = submissionRepository.findById(sourceId)
                .orElseThrow(() -> new SubmissionNotFoundException(sourceId));
        Submission target = submissionRepository.findById(targetId)
                .orElseThrow(() -> new SubmissionNotFoundException(targetId));

        if (relationRepository.existsBySubmissionAAndSubmissionBAndRelationType(source, target, type)) {
            throw new IllegalArgumentException("That link already exists.");
        }

        SubmissionRelation relation = new SubmissionRelation();
        relation.setSubmissionA(source);
        relation.setSubmissionB(target);
        relation.setRelationType(type);
        relation.setCreatedBy(creator);
        relation = relationRepository.save(relation);

        auditService.record(source, AuditAction.RELATION_ADDED,
                "Linked as " + label(type) + " '" + target.getTitle() + "'", creator);

        return relation;
    }

    @Transactional
    public void deleteRelation(Long relationId) {
        relationRepository.deleteById(relationId);
    }

    private String label(RelationType type) {
        return switch (type) {
            case INSPIRED_BY -> "inspired by";
            case EXTENDS -> "extending";
            case RELATED -> "related to";
        };
    }
}
