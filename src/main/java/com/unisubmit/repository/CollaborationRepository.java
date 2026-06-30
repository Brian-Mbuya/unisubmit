package com.unisubmit.repository;

import com.unisubmit.domain.Collaboration;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborationRepository extends JpaRepository<Collaboration, Long> {

    @Query("SELECT c FROM Collaboration c WHERE c.user1 = :user OR c.user2 = :user ORDER BY c.createdAt DESC")
    List<Collaboration> findByUserOrderByCreatedAtDesc(User user);
    
    @Query("SELECT COUNT(c) > 0 FROM Collaboration c WHERE ((c.user1 = :user1 AND c.user2 = :user2) OR (c.user1 = :user2 AND c.user2 = :user1)) AND c.submission = :submission")
    boolean existsCollaboration(User user1, User user2, Submission submission);

    @Query("""
            SELECT COUNT(c) > 0 FROM Collaboration c
            WHERE c.submission = :submission
            AND (c.user1 = :user OR c.user2 = :user)
            """)
    boolean existsByUserAndSubmission(User user, Submission submission);

    @Query("SELECT COUNT(c) > 0 FROM Collaboration c WHERE c.user1 = :user OR c.user2 = :user")
    boolean existsByUser(User user);
}
