package com.unisubmit.repository;

import com.unisubmit.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByUnitIdOrderByCreatedAtDesc(Long unitId);
    List<Announcement> findByLecturerIdOrderByCreatedAtDesc(Long lecturerId);
}
