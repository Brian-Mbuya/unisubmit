package com.unisubmit.repository;

import com.unisubmit.domain.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    List<Curriculum> findByProgrammeId(Long programmeId);
    List<Curriculum> findByUnitId(Long unitId);
}
