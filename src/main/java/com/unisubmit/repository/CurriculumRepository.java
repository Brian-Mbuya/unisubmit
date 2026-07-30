package com.unisubmit.repository;

import com.unisubmit.domain.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    List<Curriculum> findByProgrammeId(Long programmeId);
    List<Curriculum> findByUnitId(Long unitId);

    /**
     * The units that make up one class — programme + year + semester. This is the single
     * query the whole class-rep feature is scoped by: a rep may only ever act on rows
     * returned here for THEIR OWN profile, which is what keeps the powers contained.
     */
    List<Curriculum> findByProgrammeIdAndYearOfStudyAndSemesterNumber(
            Long programmeId, Integer yearOfStudy, Integer semesterNumber);

    Optional<Curriculum> findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
            Long programmeId, Long unitId, Integer yearOfStudy, Integer semesterNumber);
}
