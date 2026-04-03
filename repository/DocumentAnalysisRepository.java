package com.chuka.irir.repository;

import com.chuka.irir.model.DocumentAnalysis;
import com.chuka.irir.model.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentAnalysisRepository extends JpaRepository<DocumentAnalysis, Long> {

    List<DocumentAnalysis> findBySubmissionIdOrderByCreatedAtDescIdDesc(Long submissionId);

    @EntityGraph(attributePaths = {"keywords"})
    Optional<DocumentAnalysis> findFirstBySubmissionIdOrderByCreatedAtDescIdDesc(Long submissionId);

    default List<DocumentAnalysis> findBySubmissionIdOrderByVersionDesc(Long submissionId) {
        return findBySubmissionIdOrderByCreatedAtDescIdDesc(submissionId);
    }

    default Optional<DocumentAnalysis> findFirstBySubmissionIdOrderByVersionDesc(Long submissionId) {
        return findFirstBySubmissionIdOrderByCreatedAtDescIdDesc(submissionId);
    }

    @Query("""
            select da
            from DocumentAnalysis da
            join fetch da.submission s
            left join fetch da.keywords
            left join fetch s.student
            left join fetch s.lecturer
            left join fetch s.unit
            left join fetch s.department
            where da.id = (
                select max(innerDa.id)
                from DocumentAnalysis innerDa
                where innerDa.submission.id = da.submission.id
            )
            """)
    List<DocumentAnalysis> findLatestAnalysesWithSubmissionContext();

    @Query("""
            select da
            from DocumentAnalysis da
            join fetch da.submission s
            left join fetch da.keywords
            left join fetch s.student
            left join fetch s.lecturer
            left join fetch s.unit
            left join fetch s.department
            where da.id in (
                select max(innerDa.id)
                from DocumentAnalysis innerDa
                where innerDa.submission.id in :submissionIds
                group by innerDa.submission.id
            )
            """)
    List<DocumentAnalysis> findLatestAnalysesBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);

    @Query("""
            select da
            from DocumentAnalysis da
            join fetch da.submission s
            left join fetch da.keywords
            left join fetch s.student
            left join fetch s.lecturer
            left join fetch s.unit
            left join fetch s.department
            where da.id in (
                select max(innerDa.id)
                from DocumentAnalysis innerDa
                join innerDa.submission candidate
                where candidate.id <> :submissionId
                  and candidate.type = :submissionType
                  and (
                    (:unitId is not null and candidate.unit.id = :unitId)
                    or (:departmentId is not null and candidate.department.id = :departmentId)
                  )
                group by candidate.id
            )
            order by da.createdAt desc, da.id desc
            """)
    List<DocumentAnalysis> findCandidateAnalyses(@Param("submissionId") Long submissionId,
                                                 @Param("submissionType") SubmissionType submissionType,
                                                 @Param("unitId") Long unitId,
                                                 @Param("departmentId") Long departmentId);
}
