package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "teaching_assignments")
public class TeachingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lecturer_profile_id")
    private LecturerProfile lecturer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @Column(name = "academic_year")
    private String academicYear;

    @Column(name = "semester")
    private String semester;

    @Column(name = "assigned_date")
    private LocalDateTime assignedDate = LocalDateTime.now();

    @Column(name = "role")
    private String role = "PRIMARY"; // PRIMARY, ASSISTANT, SUBSTITUTE

    @Column(name = "status")
    private String status = "ACTIVE"; // ACTIVE, PAST

    @Column(name = "workload_hours")
    private Integer workloadHours;
}
