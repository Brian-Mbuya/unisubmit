package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "submission_similarities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"submission_a_id", "submission_b_id"})
})
public class SubmissionSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_a_id")
    private Submission submissionA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_b_id")
    private Submission submissionB;

    @Column(nullable = false)
    private Double similarityScore;

    @ElementCollection
    @CollectionTable(name = "similarity_keywords", joinColumns = @JoinColumn(name = "similarity_id"))
    @Column(name = "keyword")
    private List<String> matchedKeywords;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
