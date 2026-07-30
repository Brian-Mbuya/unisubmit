package com.unisubmit.repository;

import com.unisubmit.domain.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(Long userId);

    /** One active code per user: a fresh request supersedes whatever was issued before it. */
    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.used = true WHERE c.user.id = :userId AND c.used = false")
    void invalidateActiveCodes(@Param("userId") Long userId);
}
