package com.chuka.irir.repository;

import com.chuka.irir.model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByCourseIdOrderByNameAsc(Long courseId);
}
