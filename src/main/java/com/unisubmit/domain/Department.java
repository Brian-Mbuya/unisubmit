package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full name, e.g. "Department of Computer Science" */
    @Column(nullable = false)
    private String name;

    /** Short code, e.g. "CS" */
    @Column(nullable = false)
    private String code;

    @ManyToOne(optional = false)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @OneToMany(mappedBy = "department")
    private List<Unit> units = new ArrayList<>();

    @OneToMany(mappedBy = "department")
    private List<Course> courses = new ArrayList<>();
}
