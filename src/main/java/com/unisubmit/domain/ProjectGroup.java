package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "project_groups")
public class ProjectGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** The student who created / leads this group */
    @ManyToOne(optional = false)
    @JoinColumn(name = "leader_id")
    private User leader;

    /** All group members (leader is also included in this set for convenience) */
    @ManyToMany
    @JoinTable(
        name = "project_group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> members = new ArrayList<>();

}
