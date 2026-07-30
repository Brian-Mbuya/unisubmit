package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "lecturer_profiles")
public class LecturerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "staff_number", unique = true, nullable = false)
    private String staffNumber;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "academic_rank")
    private String academicRank;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "specialization")
    private String specialization;

    @Column(name = "office")
    private String office;

    @Column(name = "phone")
    private String phone;

    // The generic catalog units a lecturer is approved to teach
    @ManyToMany
    @JoinTable(
        name = "lecturer_qualified_units",
        joinColumns = @JoinColumn(name = "lecturer_profile_id"),
        inverseJoinColumns = @JoinColumn(name = "unit_id")
    )
    private List<Unit> qualifiedUnits = new ArrayList<>();
    
    // The specific active teaching assignments
    @OneToMany(mappedBy = "lecturer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeachingAssignment> assignments = new ArrayList<>();
}
