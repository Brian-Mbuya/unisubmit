package com.chuka.irir.repository;

import com.chuka.irir.model.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findBySubmissionId(Long submissionId);

    @Query("""
            select sf.submission.id, count(sf)
            from SubmissionFile sf
            where sf.submission.id in :submissionIds
            group by sf.submission.id
            """)
    List<Object[]> countBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);
}
