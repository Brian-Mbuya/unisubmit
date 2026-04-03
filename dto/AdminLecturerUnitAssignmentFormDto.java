package com.chuka.irir.dto;

import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class AdminLecturerUnitAssignmentFormDto {

    @NotNull(message = "Lecturer is required")
    private Long lecturerId;

    private Set<Long> unitIds = new LinkedHashSet<>();

    public Long getLecturerId() {
        return lecturerId;
    }

    public void setLecturerId(Long lecturerId) {
        this.lecturerId = lecturerId;
    }

    public Set<Long> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(Set<Long> unitIds) {
        this.unitIds = unitIds;
    }
}
