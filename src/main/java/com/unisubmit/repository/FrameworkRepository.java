package com.unisubmit.repository;

import com.unisubmit.domain.Framework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FrameworkRepository extends JpaRepository<Framework, Long> {
    Optional<Framework> findByNameIgnoreCase(String name);
    List<Framework> findAllByOrderByNameAsc();
}
