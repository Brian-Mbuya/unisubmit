package com.unisubmit.repository;

import com.unisubmit.domain.AIInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIInsightRepository extends JpaRepository<AIInsight, Long> {
}
