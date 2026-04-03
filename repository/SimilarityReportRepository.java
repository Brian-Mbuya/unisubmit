package com.chuka.irir.repository;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.SimilarityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SimilarityReport} entities.
 */
@Repository
public interface SimilarityReportRepository extends JpaRepository<SimilarityReport, Long> {

    Optional<SimilarityReport> findFirstBySourceProjectOrderByGeneratedAtDesc(Project sourceProject);

    List<SimilarityReport> findBySimilarityScoreGreaterThanEqual(Double threshold);

    List<SimilarityReport> findAllByOrderByGeneratedAtDesc();

    /** Count flagged reports. Used for dashboard metrics. */
    long countByFlaggedTrue();
}
