package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "curriculum")
public class Curriculum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "programme_id")
    private Course programme; // A Course/Programme

    @ManyToOne(optional = false)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @Column(name = "year_of_study")
    private Integer yearOfStudy; // e.g. Year 1, Year 2
    
    @Column(name = "semester_number")
    private Integer semesterNumber;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "status")
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

}
