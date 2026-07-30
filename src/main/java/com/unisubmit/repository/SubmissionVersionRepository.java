package com.unisubmit.repository;

import com.unisubmit.domain.SubmissionVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {
    Optional<SubmissionVersion> findByFilePath(String filePath);
}
