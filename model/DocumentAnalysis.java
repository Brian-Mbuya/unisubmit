package com.chuka.irir.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_analyses", indexes = {
        @Index(name = "idx_document_analysis_submission_created", columnList = "submission_id, created_at"),
        @Index(name = "idx_document_analysis_submission_id", columnList = "submission_id, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "document_analysis_keywords", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "keyword", nullable = false, length = 120)
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "document_analysis_topics", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "topic", nullable = false, length = 120)
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
