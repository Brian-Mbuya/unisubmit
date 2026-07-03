package com.unisubmit.repository;

import com.unisubmit.domain.LecturerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerProfileRepository extends JpaRepository<LecturerProfile, Long> {
    Optional<LecturerProfile> findByStaffNumber(String staffNumber);
    Optional<LecturerProfile> findByStaffNumberIgnoreCase(String staffNumber);
    Optional<LecturerProfile> findByUser_Username(String username);
    List<LecturerProfile> findByUser_DeletedFalse();
}
