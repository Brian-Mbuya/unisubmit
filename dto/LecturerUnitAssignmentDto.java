package com.chuka.irir.dto;

import jakarta.validation.constraints.NotNull;

public class LecturerUnitAssignmentDto {

    @NotNull
    private Long departmentId;

    @NotNull
    private Long courseId;

    @NotNull
    private Long unitId;

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }
}
