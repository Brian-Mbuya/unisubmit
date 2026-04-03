package com.chuka.irir.repository;

import com.chuka.irir.model.CollaborationRequest;
import com.chuka.irir.model.CollaborationStatus;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborationRequestRepository extends JpaRepository<CollaborationRequest, Long> {

    List<CollaborationRequest> findBySenderOrderByCreatedAtDesc(User sender);

    List<CollaborationRequest> findByReceiverOrderByCreatedAtDesc(User receiver);

    List<CollaborationRequest> findByStatus(CollaborationStatus status);
}
