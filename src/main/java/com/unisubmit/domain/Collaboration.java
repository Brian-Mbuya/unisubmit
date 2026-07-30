package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "collaborations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_1_id", "user_2_id", "submission_id"})
})
public class Collaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_1_id")
    private User user1;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_2_id")
    private User user2;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User getOtherUser(User me) {
        if (user1.getId().equals(me.getId())) return user2;
        if (user2.getId().equals(me.getId())) return user1;
        return null;
    }
}
