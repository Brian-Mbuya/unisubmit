package com.chuka.irir.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO for creating a new student project submission.
 */
@Getter
@Setter
public class ProjectCreateDto {

    @NotBlank(message = "Project title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @Size(max = 5000, message = "Abstract must be 5000 characters or less")
    private String abstractText;

    /**
     * Comma-separated keywords entered by the student.
     * Example: "AI, healthcare, machine learning"
     */
    private String keywords;

    /**
     * Academic year (optional). If not provided, the service will default to the current year.
     */
    private Integer academicYear;

    @NotNull(message = "Please select a unit.")
    private Long unitId;

    /**
     * Uploaded files (PDF, DOCX, ZIP).
     */
    private MultipartFile[] files;
}
