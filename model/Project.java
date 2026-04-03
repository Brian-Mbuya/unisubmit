package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA entity representing a research project submitted by a student.
 *
 * A project goes through a lifecycle defined by {@link ProjectStatus}:
 * DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED/FLAGGED → INCUBATION.
 *
 * <p>Each project may have multiple {@link ProjectFile}s, {@link Review}s, and
 * {@link SimilarityReport}s associated with it.</p>
 *
 * <p>Keywords are stored in a separate collection table for efficient
 * collaborator matching (UC-04) and search indexing.</p>
 */
@Entity
@Table(name = "projects")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Title of the research project. */
    @NotBlank(message = "Project title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    @Column(nullable = false)
    private String title;

    /** Abstract or summary of the project. Stored as TEXT for large content. */
    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    /** Current status in the submission lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    /** The student who submitted this project. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    /** The assigned supervisor/lecturer for this project. May be null initially. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    /** Unit selected by the student. The supervising lecturer is derived from this unit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /** Academic year when the project was created (e.g., 2025). */
    @Column(name = "academic_year")
    private Integer academicYear;

    /**
     * Keywords/tags associated with this project.
     * Used for collaborator recommendation (UC-04) and search indexing.
     * Stored in a separate {@code project_keywords} table.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_keywords", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "keyword", length = 100)
    @Builder.Default
    private Set<String> keywords = new HashSet<>();

    /** Full text extracted from uploaded files (via Apache Tika). Used for similarity detection. */
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    /** Files attached to this project (PDF, DOCX, ZIP). */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectFile> files = new ArrayList<>();

    /** Reviews made by supervisors or directorate members. */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    /** Similarity reports comparing this project against others in the repository. */
    @OneToMany(mappedBy = "sourceProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SimilarityReport> similarityReports = new ArrayList<>();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Lifecycle Callbacks ====================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /** Adds a file to this project and sets the back-reference. */
    public void addFile(ProjectFile file) {
        if (file == null) {
            return;
        }
        if (files == null) {
            files = new ArrayList<>();
        }
        files.add(file);
        file.setProject(this);
    }

    /** Removes a file from this project. */
    public void removeFile(ProjectFile file) {
        if (file == null) {
            return;
        }
        if (files != null) {
            files.remove(file);
        }
        file.setProject(null);
    }
}
