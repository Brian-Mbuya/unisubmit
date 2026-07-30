package com.unisubmit.repository;

import com.unisubmit.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByAdmissionNumber(String admissionNumber);
    Optional<StudentProfile> findByAdmissionNumberIgnoreCase(String admissionNumber);
    Optional<StudentProfile> findByUser_Username(String username);
    List<StudentProfile> findByUser_DeletedFalse();
    List<StudentProfile> findByProgrammeId(Long programmeId);

    /** Used at login to resolve the class-rep authority without touching the lazy association. */
    Optional<StudentProfile> findByUser_Id(Long userId);

    /**
     * The class roster: everyone in the same programme/year/semester as the rep. This
     * triple IS the definition of a "class" here — there is no ClassGroup entity, and
     * adding one would duplicate what Curriculum already keys on.
     */
    List<StudentProfile> findByProgrammeIdAndCurrentYearAndCurrentSemesterAndUser_DeletedFalse(
            Long programmeId, Integer currentYear, Integer currentSemester);
}
