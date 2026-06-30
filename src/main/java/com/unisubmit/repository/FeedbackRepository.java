package com.unisubmit.repository;

import com.unisubmit.domain.Feedback;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsByLecturer(User lecturer);
}
