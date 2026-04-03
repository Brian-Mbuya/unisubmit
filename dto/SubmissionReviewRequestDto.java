package com.chuka.irir.dto;

import com.chuka.irir.model.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SubmissionReviewRequestDto {

    @NotNull
    private ReviewDecision decision;

    @Size(max = 2000)
    private String remarks;

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
