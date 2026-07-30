package com.unisubmit.repository;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DB-level idempotency for the AI pipeline: the conditional {@code UPDATE ... WHERE status
 * IN (...)} must let exactly one caller win the PENDING→PROCESSING claim, and must refuse a
 * retry of a run that is already PROCESSING. Runs against the real (H2) schema, so it also
 * exercises the secondary-index DDL added in 2.8a. Wrapped in a rolled-back transaction.
 */
@SpringBootTest
@Transactional
class AiPipelineIdempotencyTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AIInsightRepository insightRepository;

    private Long insightId;

    @BeforeEach
    void seed() {
        User student = new User();
        student.setUsername("idempotency-student-" + hashCode());
        student.setPassword("x");
        student.setName("Idempotency Student");
        student.setRole(Role.STUDENT);
        em.persist(student);

        Submission submission = new Submission();
        submission.setTitle("Idempotency Fixture");
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        em.persist(submission);

        AIInsight insight = new AIInsight();
        insight.setSubmission(submission);
        insight.setStatus(AIInsightStatus.PENDING);
        em.persist(insight);

        em.flush();
        em.clear();
        insightId = insight.getId();
    }

    @Test
    void onlyOneCallerClaimsPendingForProcessing() {
        int first = insightRepository.transition(insightId, AIInsightStatus.PROCESSING,
                List.of(AIInsightStatus.PENDING));
        int second = insightRepository.transition(insightId, AIInsightStatus.PROCESSING,
                List.of(AIInsightStatus.PENDING));

        assertEquals(1, first, "the first claim wins");
        assertEquals(0, second, "the second claim finds nothing PENDING to take");

        em.clear();
        assertEquals(AIInsightStatus.PROCESSING,
                insightRepository.findById(insightId).orElseThrow().getStatus());
    }

    @Test
    void retryFromProcessingIsRefused() {
        insightRepository.transition(insightId, AIInsightStatus.PROCESSING,
                List.of(AIInsightStatus.PENDING)); // now PROCESSING

        int retried = insightRepository.transition(insightId, AIInsightStatus.PENDING,
                List.of(AIInsightStatus.FAILED));

        assertEquals(0, retried, "only a FAILED run is retriable — a PROCESSING run is not");

        em.clear();
        assertEquals(AIInsightStatus.PROCESSING,
                insightRepository.findById(insightId).orElseThrow().getStatus());
    }
}
