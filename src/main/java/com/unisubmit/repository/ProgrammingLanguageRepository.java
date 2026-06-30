package com.unisubmit.repository;

import com.unisubmit.domain.ProgrammingLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgrammingLanguageRepository extends JpaRepository<ProgrammingLanguage, Long> {
    Optional<ProgrammingLanguage> findByNameIgnoreCase(String name);
    List<ProgrammingLanguage> findAllByOrderByNameAsc();
}
