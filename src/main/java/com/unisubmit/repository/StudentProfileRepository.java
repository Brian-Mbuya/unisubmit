package com.unisubmit.repository;

import com.unisubmit.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByAdmissionNumber(String admissionNumber);
    Optional<StudentProfile> findByUser_Username(String username);
    List<StudentProfile> findByUser_DeletedFalse();
}
