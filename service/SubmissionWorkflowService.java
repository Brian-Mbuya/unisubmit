package com.chuka.irir.service;

import com.chuka.irir.dto.AdminProjectAssignmentDto;
import com.chuka.irir.dto.CourseworkSubmissionCreateDto;
import com.chuka.irir.dto.FinalYearSubmissionCreateDto;
import com.chuka.irir.dto.ProjectAssignmentSummaryDto;
import com.chuka.irir.dto.SubmissionDetailDto;
import com.chuka.irir.dto.SubmissionInsightDto;
import com.chuka.irir.dto.SubmissionReviewRequestDto;
import com.chuka.irir.exception.FileStorageException;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Course;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.Review;
import com.chuka.irir.model.ReviewDecision;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.Submission;
import com.chuka.irir.model.SubmissionFile;
import com.chuka.irir.model.SubmissionStatus;
import com.chuka.irir.model.SubmissionType;
import com.chuka.irir.model.Unit;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.CourseRepository;
import com.chuka.irir.repository.DocumentAnalysisRepository;
import com.chuka.irir.repository.LecturerUnitAssignmentRepository;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.ReviewRepository;
import com.chuka.irir.repository.SubmissionFileRepository;
import com.chuka.irir.repository.SubmissionRepository;
import com.chuka.irir.repository.UnitRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class SubmissionWorkflowService {

    private final SubmissionRepository submissionRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final CourseRepository courseRepository;
    private final LecturerUnitAssignmentRepository lecturerUnitAssignmentRepository;
    private final ReviewRepository reviewRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final DocumentAnalysisRepository documentAnalysisRepository;
    private final FileStorageService fileStorageService;
    private final AIAnalysisService aiAnalysisService;
    private final SimilarityService similarityService;

    public SubmissionWorkflowService(SubmissionRepository submissionRepository,
                                     ProjectRepository projectRepository,
                                     UserRepository userRepository,
                                     UnitRepository unitRepository,
                                     CourseRepository courseRepository,
                                     LecturerUnitAssignmentRepository lecturerUnitAssignmentRepository,
                                     ReviewRepository reviewRepository,
                                     SubmissionFileRepository submissionFileRepository,
                                     DocumentAnalysisRepository documentAnalysisRepository,
                                     FileStorageService fileStorageService,
                                     AIAnalysisService aiAnalysisService,
                                     SimilarityService similarityService) {
        this.submissionRepository = submissionRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.courseRepository = courseRepository;
        this.lecturerUnitAssignmentRepository = lecturerUnitAssignmentRepository;
        this.reviewRepository = reviewRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.documentAnalysisRepository = documentAnalysisRepository;
        this.fileStorageService = fileStorageService;
        this.aiAnalysisService = aiAnalysisService;
        this.similarityService = similarityService;
    }

    public Project assignLecturerToFinalYearProject(Long projectId, AdminProjectAssignmentDto dto) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        User lecturer = getLecturer(dto.getLecturerId());
        project.setSupervisor(lecturer);
        return projectRepository.save(project);
    }

    public Submission submitFinalYearProject(FinalYearSubmissionCreateDto dto, User student) {
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", dto.getProjectId()));
        if (project.getSubmittedBy() == null || !Objects.equals(project.getSubmittedBy().getId(), student.getId())) {
            throw new AccessDeniedException("You can only submit your own final-year project.");
        }
        if (project.getSupervisor() == null) {
            throw new IllegalStateException("A lecturer must be assigned before final-year submission.");
        }

        Submission submission = Submission.builder()
                .type(SubmissionType.FINAL_YEAR_PROJECT)
                .status(SubmissionStatus.SUBMITTED)
                .title(normalizeTitle(dto.getTitle(), project.getTitle()))
                .description(normalizeOptional(dto.getDescription()))
                .student(student)
                .lecturer(project.getSupervisor())
                .finalYearProject(project)
                .build();

        submission.setUnit(project.getUnit());
        attachFiles(submission, dto.getFiles());
        Submission saved = submissionRepository.save(submission);
        aiAnalysisService.enqueueSubmissionAnalysis(saved.getId());
        return saved;
    }

    public Submission submitCoursework(CourseworkSubmissionCreateDto dto, User student) {
        Unit unit = unitRepository.findById(dto.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", dto.getUnitId()));
        Course course = unit.getCourse();
        if (dto.getCourseId() != null && !course.getId().equals(dto.getCourseId())) {
            throw new IllegalArgumentException("The selected unit does not belong to the chosen course.");
        }
        if (dto.getDepartmentId() != null && !course.getDepartment().getId().equals(dto.getDepartmentId())) {
            throw new IllegalArgumentException("The selected course does not belong to the chosen department.");
        }
        User lecturer = resolveAssignedLecturer(unit);
        if (lecturer == null) {
            throw new IllegalStateException("The selected unit has no assigned lecturer yet.");
        }
        getLecturer(lecturer.getId());

        Submission submission = Submission.builder()
                .type(SubmissionType.COURSEWORK)
                .status(SubmissionStatus.SUBMITTED)
                .title(normalizeTitle(dto.getTitle(), unit.getName() + " Coursework"))
                .description(normalizeOptional(dto.getDescription()))
                .student(student)
                .lecturer(lecturer)
                .department(course.getDepartment())
                .course(course)
                .unit(unit)
                .build();

        attachFiles(submission, dto.getFiles());
        Submission saved = submissionRepository.save(submission);
        aiAnalysisService.enqueueSubmissionAnalysis(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Submission> getStudentSubmissions(Long studentId) {
        return submissionRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<Submission> getLecturerSubmissions(Long lecturerId) {
        return submissionRepository.findByLecturerIdOrderBySubmittedAtDesc(lecturerId);
    }

    @Transactional(readOnly = true)
    public List<Submission> getLecturerSubmissionsByUnit(Long lecturerId, Long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        User assignedLecturer = resolveAssignedLecturer(unit);
        if (assignedLecturer == null || !Objects.equals(assignedLecturer.getId(), lecturerId)) {
            throw new AccessDeniedException("You can only filter using units assigned to you.");
        }
        return submissionRepository.findByLecturerIdAndUnitIdOrderBySubmittedAtDesc(lecturerId, unitId);
    }

    @Transactional(readOnly = true)
    public List<com.chuka.irir.dto.SubmissionSummaryDto> getStudentSubmissionSummaries(Long studentId) {
        List<Submission> submissions = submissionRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
        Map<Long, com.chuka.irir.model.DocumentAnalysis> latestAnalyses = latestAnalysesBySubmissionId(submissions);
        Map<Long, Integer> fileCounts = fileCountsBySubmissionId(submissions);
        Map<Long, Integer> reviewCounts = reviewCountsBySubmissionId(submissions);
        return submissions.stream()
                .map(submission -> toSummaryDto(
                        submission,
                        latestAnalyses.get(submission.getId()),
                        fileCounts.getOrDefault(submission.getId(), 0),
                        reviewCounts.getOrDefault(submission.getId(), 0)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.chuka.irir.dto.SubmissionSummaryDto> getLecturerSubmissionSummaries(Long lecturerId, Long unitId) {
        List<Submission> submissions = unitId == null
                ? submissionRepository.findByLecturerIdOrderBySubmittedAtDesc(lecturerId)
                : getLecturerSubmissionsByUnit(lecturerId, unitId);
        Map<Long, com.chuka.irir.model.DocumentAnalysis> latestAnalyses = latestAnalysesBySubmissionId(submissions);
        Map<Long, Integer> fileCounts = fileCountsBySubmissionId(submissions);
        Map<Long, Integer> reviewCounts = reviewCountsBySubmissionId(submissions);
        return submissions.stream()
                .map(submission -> toSummaryDto(
                        submission,
                        latestAnalyses.get(submission.getId()),
                        fileCounts.getOrDefault(submission.getId(), 0),
                        reviewCounts.getOrDefault(submission.getId(), 0)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDetailDto getSubmissionDetailForLecturer(Long submissionId, Long lecturerId) {
        Submission submission = getSubmissionForLecturer(submissionId, lecturerId);
        return toDetailDto(submission);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailDto getSubmissionDetailForStudent(Long submissionId, Long studentId) {
        Submission submission = getSubmissionForStudent(submissionId, studentId);
        return toDetailDto(submission);
    }

    @Transactional(readOnly = true)
    public Submission getSubmissionForLecturer(Long submissionId, Long lecturerId) {
        Submission submission = getSubmission(submissionId);
        if (!isSubmissionAssignedToLecturer(submission, lecturerId)) {
            throw new AccessDeniedException("You can only view submissions assigned to you.");
        }
        initializeSubmission(submission);
        return submission;
    }

    @Transactional(readOnly = true)
    public Submission getSubmissionForStudent(Long submissionId, Long studentId) {
        Submission submission = getSubmission(submissionId);
        if (!Objects.equals(submission.getStudent().getId(), studentId)) {
            throw new AccessDeniedException("You can only view your own submissions.");
        }
        initializeSubmission(submission);
        return submission;
    }

    public Review reviewSubmission(Long submissionId, Long lecturerId, SubmissionReviewRequestDto dto) {
        Submission submission = getSubmissionForLecturer(submissionId, lecturerId);
        User lecturer = getLecturer(lecturerId);

        Review review = Review.builder()
                .submission(submission)
                .reviewer(lecturer)
                .decision(dto.getDecision())
                .comments(normalizeOptional(dto.getRemarks()))
                .build();

        submission.setStatus(mapStatus(dto.getDecision()));
        reviewRepository.save(review);
        submissionRepository.save(submission);
        return review;
    }

    public void requestReanalysis(Long submissionId, Long actorId, boolean lecturerContext) {
        if (lecturerContext) {
            getSubmissionForLecturer(submissionId, actorId);
        } else {
            getSubmissionForStudent(submissionId, actorId);
        }
        aiAnalysisService.enqueueSubmissionAnalysis(submissionId);
    }

    @Transactional(readOnly = true)
    public Resource downloadSubmissionFileForLecturer(Long submissionId, Long fileId, Long lecturerId) {
        Submission submission = getSubmissionForLecturer(submissionId, lecturerId);
        SubmissionFile file = submission.getFiles().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), fileId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SubmissionFile", "id", fileId));
        return fileStorageService.loadAsResource(file.getStoragePath());
    }

    @Transactional(readOnly = true)
    public List<ProjectAssignmentSummaryDto> getFinalYearAssignmentsOverview() {
        List<Project> projects = projectRepository.findAll();
        List<ProjectAssignmentSummaryDto> results = new ArrayList<>();
        for (Project project : projects) {
            List<Submission> submissions = submissionRepository.findByFinalYearProjectIdOrderBySubmittedAtDesc(project.getId());
            Submission latest = submissions.stream()
                    .max(Comparator.comparing(Submission::getSubmittedAt))
                    .orElse(null);
            results.add(ProjectAssignmentSummaryDto.from(project, latest));
        }
        return results;
    }

    private Submission getSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", "id", submissionId));
    }

    private User getLecturer(Long lecturerId) {
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        if (lecturer.getRoles() == null || !lecturer.getRoles().contains(Role.SUPERVISOR)) {
            throw new IllegalArgumentException("The selected user is not a lecturer.");
        }
        return lecturer;
    }

    private User resolveAssignedLecturer(Unit unit) {
        return unit == null || unit.getId() == null
                ? null
                : lecturerUnitAssignmentRepository.findFirstByUnitId(unit.getId())
                .map(assignment -> assignment.getLecturer())
                .orElse(null);
    }

    private void attachFiles(Submission submission, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new FileStorageException("At least one file is required.");
        }
        boolean attached = false;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.store(file);
            submission.addFile(SubmissionFile.builder()
                    .fileName(stored.originalName())
                    .fileType(stored.contentType())
                    .fileSize(stored.size())
                    .storagePath(stored.storagePath())
                    .build());
            attached = true;
        }
        if (!attached) {
            throw new FileStorageException("At least one file is required.");
        }
    }

    private SubmissionStatus mapStatus(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> SubmissionStatus.APPROVED;
            case REJECTED -> SubmissionStatus.REJECTED;
            case REVISION_REQUESTED -> SubmissionStatus.REVISION_REQUESTED;
        };
    }

    private void initializeSubmission(Submission submission) {
        if (submission.getFiles() != null) {
            submission.getFiles().size();
        }
        if (submission.getReviews() != null) {
            submission.getReviews().size();
        }
        if (submission.getStudent() != null) {
            submission.getStudent().getFullName();
        }
        if (submission.getLecturer() != null) {
            submission.getLecturer().getFullName();
        }
        if (submission.getDepartment() != null) {
            submission.getDepartment().getName();
        }
        if (submission.getCourse() != null) {
            submission.getCourse().getName();
        }
        if (submission.getUnit() != null) {
            submission.getUnit().getName();
        }
    }

    private String normalizeTitle(String title, String fallback) {
        String normalized = normalizeOptional(title);
        return normalized != null ? normalized : fallback;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private com.chuka.irir.dto.SubmissionSummaryDto toSummaryDto(Submission submission,
                                                                 com.chuka.irir.model.DocumentAnalysis latestAnalysis,
                                                                 int fileCount,
                                                                 int reviewCount) {
        SubmissionInsightDto insights = similarityService.buildInsights(submission, latestAnalysis);
        return com.chuka.irir.dto.SubmissionSummaryDto.from(
                submission,
                insights.analysisStatus(),
                insights.latestAnalysis() == null ? null : insights.latestAnalysis().version(),
                insights.latestAnalysis() == null ? null : insights.latestAnalysis().summary(),
                insights.latestAnalysis() == null ? List.of() : insights.latestAnalysis().keywords(),
                insights.highestSimilarityScore(),
                insights.highSimilarityWarning(),
                insights.similarSubmissions().stream()
                        .findFirst()
                        .map(similar -> similar.basedOnKeywords())
                        .orElse(List.of()),
                fileCount,
                reviewCount
        );
    }

    private SubmissionDetailDto toDetailDto(Submission submission) {
        initializeSubmission(submission);
        SubmissionInsightDto insights = similarityService.buildInsights(submission, aiAnalysisService.getLatestAnalysis(submission.getId()));
        return new SubmissionDetailDto(
                submission.getId(),
                submission.getType(),
                submission.getTitle(),
                submission.getDescription(),
                submission.getStatus().name(),
                submission.getStudent() == null ? null : submission.getStudent().getFullName(),
                submission.getLecturer() == null ? null : submission.getLecturer().getFullName(),
                submission.getDepartment() == null ? null : submission.getDepartment().getName(),
                submission.getCourse() == null ? null : submission.getCourse().getName(),
                submission.getUnit() == null ? null : submission.getUnit().getName(),
                submission.getSubmittedAt(),
                submission.getFiles() == null ? 0 : submission.getFiles().size(),
                submission.getReviews() == null ? 0 : submission.getReviews().size(),
                insights
        );
    }

    private boolean isSubmissionAssignedToLecturer(Submission submission, Long lecturerId) {
        if (submission == null || lecturerId == null) {
            return false;
        }
        if (submission.getType() == SubmissionType.COURSEWORK) {
            User assignedLecturer = resolveAssignedLecturer(submission.getUnit());
            return assignedLecturer != null && Objects.equals(assignedLecturer.getId(), lecturerId);
        }
        return submission.getLecturer() != null && Objects.equals(submission.getLecturer().getId(), lecturerId);
    }

    private Map<Long, com.chuka.irir.model.DocumentAnalysis> latestAnalysesBySubmissionId(List<Submission> submissions) {
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, com.chuka.irir.model.DocumentAnalysis> analyses = new HashMap<>();
        for (com.chuka.irir.model.DocumentAnalysis analysis : documentAnalysisRepository.findLatestAnalysesBySubmissionIds(submissionIds)) {
            analyses.put(analysis.getSubmission().getId(), analysis);
        }
        return analyses;
    }

    private Map<Long, Integer> fileCountsBySubmissionId(List<Submission> submissions) {
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : submissionFileRepository.countBySubmissionIds(submissionIds)) {
            counts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    private Map<Long, Integer> reviewCountsBySubmissionId(List<Submission> submissions) {
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : reviewRepository.countBySubmissionIds(submissionIds)) {
            counts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }
}
