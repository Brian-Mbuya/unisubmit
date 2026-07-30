package com.unisubmit.repository;

import com.unisubmit.domain.Role;
import com.unisubmit.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(Role role);

    /** Active (non-deleted) listings used by the admin console. */
    List<User> findByDeletedFalse();
    List<User> findByRoleAndDeletedFalse(Role role);

    /** Filter by role only. */
    List<User> findByRoleAndDeletedFalseOrderByNameAsc(Role role);

    /** Count active (non-deleted) users by role. */
    long countByRoleAndDeletedFalse(Role role);

    /** Count all active users. */
    long countByDeletedFalse();

    @Query("SELECT u FROM User u WHERE u.role = com.unisubmit.domain.Role.STUDENT " +
            "AND u.deleted = false " +
            "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(u.studentProfile.admissionNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<User> searchStudents(@Param("q") String q, Pageable pageable);
}
