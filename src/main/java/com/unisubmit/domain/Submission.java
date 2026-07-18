package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "submissions", indexes = {
    @Index(name = "ix_submission_student", columnList = "student_id"),
    @Index(name = "ix_submission_curriculum", columnList = "curriculum_id")
})
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;
    
    @ManyToOne(optional = true)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @ManyToOne(optional = true)
    @JoinColumn(name = "project_group_id")
    private ProjectGroup projectGroup;

    @ManyToMany
    @JoinTable(
        name = "submission_supervisors",
        joinColumns = @JoinColumn(name = "submission_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> supervisors = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_technologies",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "technology_id"))
    private Set<Technology> technologies = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_research_areas",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "research_area_id"))
    private Set<ResearchArea> researchAreas = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_frameworks",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "framework_id"))
    private Set<Framework> frameworks = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_databases",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "database_id"))
    private Set<Database> databases = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_programming_languages",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "programming_language_id"))
    private Set<ProgrammingLanguage> programmingLanguages = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "submission_skills",
      joinColumns = @JoinColumn(name = "submission_id"),
      inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionVersion> versions = new ArrayList<>();
    
    @OneToOne(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private AIInsight aiInsight;

    @Column(name = "embedding")
    @Convert(converter = VectorConverter.class)
    private float[] embedding;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Unit getUnit() {
        return this.curriculum != null ? this.curriculum.getUnit() : null;
    }
}
