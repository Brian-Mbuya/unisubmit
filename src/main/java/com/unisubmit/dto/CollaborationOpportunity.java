package com.unisubmit.dto;

import com.unisubmit.domain.CollaborationType;
import com.unisubmit.domain.CollaborationValue;
import com.unisubmit.domain.Submission;

/**
 * Phase 8 — a collaboration match rendered from the VIEWER's perspective.
 * {@code yourProject} is the viewer's own submission in the pair; {@code partner}
 * is the other student's project; {@code youGain}/{@code theyGain} are the
 * directional gains re-oriented so "you" is always the viewer.
 */
public record CollaborationOpportunity(
        Long matchId,
        Submission yourProject,
        Submission partner,
        CollaborationValue value,
        CollaborationType type,
        String youGain,
        String theyGain,
        String pitch,
        String complementaryGaps,
        boolean assessed,
        boolean requestAlreadySent,
        String partnerDepartment,
        Integer partnerYear,
        /** Why this pair surfaced, written for THIS viewer ("you bring … they bring …"). */
        String reason,
        /** "Could mentor you" / "You could mentor them", or null when neither is senior. */
        String mentorLabel) {

    public boolean crossDepartment() {
        String mine = yourProject != null && yourProject.getUnit() != null
                && yourProject.getUnit().getDepartment() != null
                ? yourProject.getUnit().getDepartment().getName() : null;
        return partnerDepartment != null && mine != null && !partnerDepartment.equalsIgnoreCase(mine);
    }
}
