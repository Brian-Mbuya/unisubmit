package com.chuka.irir.repository;

import com.chuka.irir.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Review} entities.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Find all reviews for a specific project, ordered by most recent first. */
    List<Review> findByProjectIdOrderByReviewedAtDesc(Long projectId);

    /** Find all reviews written by a specific reviewer. */
    List<Review> findByReviewerIdOrderByReviewedAtDesc(Long reviewerId);

    /** Count reviews for a specific project. */
    long countByProjectId(Long projectId);

    List<Review> findBySubmissionIdOrderByReviewedAtDesc(Long submissionId);

    @Query("""
            select r.submission.id, count(r)
            from Review r
            where r.submission.id in :submissionIds
            group by r.submission.id
            """)
    List<Object[]> countBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);
}
