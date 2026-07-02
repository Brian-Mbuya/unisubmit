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
}
