package com.chuka.irir.repository;

import com.chuka.irir.model.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ProjectFile} entities.
 */
@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

    /** Find all files belonging to a specific project. */
    List<ProjectFile> findByProjectId(Long projectId);
}
