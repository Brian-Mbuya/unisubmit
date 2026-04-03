package com.chuka.irir.dto;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.Submission;

public record ProjectAssignmentSummaryDto(
        Long projectId,
        String projectTitle,
        String studentName,
        String assignedLecturer,
        String projectStatus,
        Long latestSubmissionId,
        String latestSubmissionStatus
) {
    public static ProjectAssignmentSummaryDto from(Project project, Submission latestSubmission) {
        return new ProjectAssignmentSummaryDto(
                project.getId(),
                project.getTitle(),
                project.getSubmittedBy() == null ? null : project.getSubmittedBy().getFullName(),
                project.getSupervisor() == null ? null : project.getSupervisor().getFullName(),
                project.getStatus() == null ? null : project.getStatus().name(),
                latestSubmission == null ? null : latestSubmission.getId(),
                latestSubmission == null || latestSubmission.getStatus() == null
                        ? null
                        : latestSubmission.getStatus().name()
        );
    }
}
