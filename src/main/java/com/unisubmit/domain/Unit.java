package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = true, unique = true)
    private String unitCode;

    @Column(name = "name", nullable = true)
    private String unitName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = true)
    private Integer creditHours = 3;

    /**
     * Optional submission deadline for this unit.
     * When set, the student dashboard shows a countdown or OVERDUE badge.
     */
    @Column(name = "submission_deadline")
    private LocalDateTime submissionDeadline;

    // Legacy fields mapped for safety during migration, then dropping them.

    @Transient
    private java.util.List<User> lecturers = new java.util.ArrayList<>();

    /**
     * Whole days remaining until the submission deadline (negative when overdue).
     * Computed here because Thymeleaf's #temporals has no between() utility.
     */
    @Transient
    public Long getDaysToDeadline() {
        if (submissionDeadline == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), submissionDeadline);
    }
}
