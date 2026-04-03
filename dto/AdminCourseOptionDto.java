package com.chuka.irir.dto;

import com.chuka.irir.model.Course;

public record AdminCourseOptionDto(
        Long id,
        String code,
        String name,
        Long departmentId,
        String departmentName
) {
    public static AdminCourseOptionDto from(Course course) {
        return new AdminCourseOptionDto(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getDepartment() == null ? null : course.getDepartment().getId(),
                course.getDepartment() == null ? null : course.getDepartment().getName()
        );
    }
}
