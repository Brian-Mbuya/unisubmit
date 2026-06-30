package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lecturer_id")
    private User lecturer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_version_id")
    private SubmissionVersion submissionVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Optional numeric grade (0–100). Null means no grade was given for this feedback entry.
     * Lecturers can leave feedback without a grade, or include one when making a final decision.
     */
    @Column
    private Integer grade;
}

