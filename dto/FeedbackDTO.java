package com.chuka.irir.dto;

import jakarta.validation.constraints.Size;

public class FeedbackDTO {
    
    public enum FeedbackAction {
        APPROVED,
        REJECTED,
        FORWARDED_TO_INCUBATION
    }

    private FeedbackAction action;

    @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
    private String comment;

    public FeedbackAction getAction() {
        return action;
    }

    public void setAction(FeedbackAction action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
