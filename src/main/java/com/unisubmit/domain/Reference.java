package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "\"references\"")
public class Reference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(columnDefinition = "TEXT")
    private String authors;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column
    private String journal;

    @Column(name = "\"year\"", length = 10)
    private String year;

    @Column(length = 255)
    private String doi;
}
