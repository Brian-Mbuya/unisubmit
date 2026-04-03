package com.chuka.irir.dto;

import com.chuka.irir.model.Unit;

public record UnitOptionDto(
        Long id,
        String code,
        String name,
        Long courseId,
        String courseName,
        Long lecturerId,
        String lecturerName
) {
    public static UnitOptionDto from(Unit unit, Long lecturerId, String lecturerName) {
        return new UnitOptionDto(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getCourse() == null ? null : unit.getCourse().getId(),
                unit.getCourse() == null ? null : unit.getCourse().getName(),
                lecturerId,
                lecturerName == null ? "Unassigned" : lecturerName
        );
    }
}
