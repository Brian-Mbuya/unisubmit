package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "semesters")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. SEM_1, SEM_2, SEM_3 */
    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    /** Units offered during this semester */
    @ManyToMany
    @JoinTable(
        name = "unit_semesters",
        joinColumns = @JoinColumn(name = "semester_id"),
        inverseJoinColumns = @JoinColumn(name = "unit_id")
    )
    private List<Unit> units = new ArrayList<>();
}
