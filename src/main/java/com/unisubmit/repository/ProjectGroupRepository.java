package com.unisubmit.repository;

import com.unisubmit.domain.ProjectGroup;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, Long> {
    List<ProjectGroup> findByLeader(User leader);
    List<ProjectGroup> findByMembersContaining(User member);
}
