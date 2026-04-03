package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a file uploaded as part of a {@link Project}.
 *
 * Supported file types include PDF, DOCX, and ZIP archives.
 * Files are stored on the filesystem (path in {@code storagePath}),
 * while metadata is stored in the database.
 *
 * <p>Apache Tika is used to extract text content from these files
 * for similarity detection and full-text search indexing.</p>
 */
@Entity
@Table(name = "project_files")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent project this file belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Original filename as uploaded by the student. */
    @NotBlank
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** MIME type of the file (e.g., "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"). */
    @Column(name = "file_type", length = 150)
    private String fileType;

    /** Absolute or relative path to the stored file on the server filesystem. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** File size in bytes. */
    @Column(name = "file_size")
    private Long fileSize;

    /** Timestamp when the file was uploaded. */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
