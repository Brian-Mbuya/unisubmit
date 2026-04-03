package com.chuka.irir.dto;

import jakarta.validation.constraints.NotNull;

public class AdminProjectAssignmentDto {

    @NotNull(message = "Lecturer is required")
    private Long lecturerId;

    public Long getLecturerId() {
        return lecturerId;
    }

    public void setLecturerId(Long lecturerId) {
        this.lecturerId = lecturerId;
    }
}
