package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Soft-delete flag — deleted accounts are hidden from listings and cannot log in. */
    @ColumnDefault("false")
    private Boolean deleted = false;

    /** Suspension — suspended accounts cannot log in until reinstated. */
    @ColumnDefault("false")
    private Boolean suspended = false;

    @Column(columnDefinition = "TEXT")
    private String suspendedReason;

    // Optional reverse mappings, though mostly accessed through repositories
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private StudentProfile studentProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private LecturerProfile lecturerProfile;

    public boolean isDeleted() {
        return deleted != null && deleted;
    }

    public boolean isSuspended() {
        return suspended != null && suspended;
    }
}
