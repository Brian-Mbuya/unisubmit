package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_profile_id")
    private StudentProfile student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @Column(name = "registration_date")
    private LocalDateTime registrationDate = LocalDateTime.now();

    @Column(name = "status")
    private String status = "ENROLLED"; // ENROLLED, WITHDRAWN, COMPLETED, FAILED

    @Column(name = "cat_marks")
    private Double catMarks;

    @Column(name = "exam_marks")
    private Double examMarks;

    @Column(name = "final_grade")
    private String finalGrade;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
