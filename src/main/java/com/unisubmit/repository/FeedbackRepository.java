package com.unisubmit.repository;

import com.unisubmit.domain.Feedback;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsByLecturer(User lecturer);

    /**
     * All feedback rows with lecturer and reviewed submission eagerly loaded,
     * used by LecturerRecommendationService to aggregate each lecturer's
     * review history against the knowledge-model tags.
     */
    @Query("""
           SELECT f FROM Feedback f
           JOIN FETCH f.lecturer
           JOIN FETCH f.submissionVersion v
           JOIN FETCH v.submission
           """)
    List<Feedback> findAllWithReviewedSubmissions();

    /**
     * Every feedback entry across ALL versions of one submission, lecturer fetched.
     * Feedback hangs off {@code SubmissionVersion}, not {@code Submission}, so a student
     * viewing their project needs this join to see the full review history rather than
     * only the comments on the latest upload.
     */
    @Query("""
           SELECT f FROM Feedback f
           JOIN FETCH f.lecturer
           WHERE f.submissionVersion.submission.id = :submissionId
           ORDER BY f.timestamp
           """)
    List<Feedback> findBySubmissionIdWithLecturer(
            @org.springframework.data.repository.query.Param("submissionId") Long submissionId);
}
