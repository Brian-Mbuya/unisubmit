package com.chuka.irir.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.LinkedHashSet;
import java.util.Set;

public class LecturerUnitAssignmentRequestDto {

    @NotEmpty(message = "Select at least one unit")
    private Set<Long> unitIds = new LinkedHashSet<>();

    public Set<Long> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(Set<Long> unitIds) {
        this.unitIds = unitIds;
    }
}
