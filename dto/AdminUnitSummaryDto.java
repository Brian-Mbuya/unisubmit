package com.chuka.irir.dto;

import com.chuka.irir.model.Unit;

public record AdminUnitSummaryDto(
        Long id,
        String code,
        String name,
        Long courseId,
        String courseName,
        String departmentName,
        Long lecturerId,
        String lecturerName
) {
    public static AdminUnitSummaryDto from(Unit unit, Long lecturerId, String lecturerName) {
        return new AdminUnitSummaryDto(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getCourse() == null ? null : unit.getCourse().getId(),
                unit.getCourse() == null ? null : unit.getCourse().getName(),
                unit.getCourse() == null || unit.getCourse().getDepartment() == null
                        ? null
                        : unit.getCourse().getDepartment().getName(),
                lecturerId,
                lecturerName
        );
    }
}
