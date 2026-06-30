package com.unisubmit.repository;

import com.unisubmit.domain.Reference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferenceRepository extends JpaRepository<Reference, Long> {
    List<Reference> findBySubmissionIdOrderByTitle(Long submissionId);
}
