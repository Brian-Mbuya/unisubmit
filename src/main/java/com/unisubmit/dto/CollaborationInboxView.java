package com.unisubmit.dto;

import com.unisubmit.domain.Collaboration;
import com.unisubmit.domain.CollaborationRequest;

import java.util.List;

public record CollaborationInboxView(
        List<CollaborationRequest> incomingRequests,
        List<CollaborationRequest> outgoingRequests,
        List<Collaboration> activeCollaborations,
        long pendingIncomingCount
) {}
