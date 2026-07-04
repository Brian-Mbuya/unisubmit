package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "submission_versions")
public class SubmissionVersion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;
    
    // File reference attributes
    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private String originalFileName;
    
    @Column(nullable = false)
    private String fileType;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(nullable = false)
    private Integer versionNumber;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
    
    @Column(length = 500)
    private String changesSummary;

    /** SHA-256 of the uploaded file — powers identical-document detection. */
    @Column(length = 64)
    private String contentHash;

    /** True when this version was uploaded after the assignment deadline (late-window upload). */
    @Column(name = "is_late", columnDefinition = "boolean default false")
    private boolean late = false;

    @ManyToOne(optional = true)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @OneToMany(mappedBy = "submissionVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Feedback> feedbacks = new ArrayList<>();
}
