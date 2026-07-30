package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "admission_number", unique = true, nullable = false)
    private String admissionNumber;

    @ManyToOne
    @JoinColumn(name = "programme_id")
    private Course programme;

    @Column(name = "current_year")
    private Integer currentYear;

    @Column(name = "current_semester")
    private Integer currentSemester;

    @Column(name = "academic_status")
    private String academicStatus = "ACTIVE"; // ACTIVE, ALUMNI, SUSPENDED, DROPPED

    /**
     * Phase 8 — opt-out for AI collaboration discovery. When false the student's
     * work is excluded from BOTH sides of collaboration matching (never surfaced
     * to others, never shown other people's matches). Defaults to true.
     */
    @Column(name = "discoverable_for_collaboration", nullable = false, columnDefinition = "boolean default true")
    private boolean discoverableForCollaboration = true;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Enrollment> enrollments = new ArrayList<>();
}
