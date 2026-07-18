package com.unisubmit.repository;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
public interface AIInsightRepository extends JpaRepository<AIInsight, Long> {

    /**
     * Atomically moves an insight from any of the {@code from} states to {@code to},
     * returning the number of rows changed: 1 means THIS caller won the claim, 0 means
     * another invocation already owns it (or the row isn't in a claimable state). This
     * single conditional UPDATE is the source of the pipeline's idempotency (2.8) — it
     * replaces read-then-write status flips that raced under concurrent re-analysis.
     */
    @Modifying
    @Transactional
    @Query("update AIInsight i set i.status = :to where i.id = :id and i.status in :from")
    int transition(@Param("id") Long id,
                   @Param("to") AIInsightStatus to,
                   @Param("from") Collection<AIInsightStatus> from);
}
