package com.unisubmit.dto;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class GroupSummaryDto {
    private Long id;
    private String name;
    private User leader;
    private List<User> members;
    private List<Submission> submissions;
    private List<GroupActivity> activities;

    public GroupSummaryDto(Long id, String name, User leader, List<User> members,
                           List<Submission> submissions, List<GroupActivity> activities) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members = members;
        this.submissions = submissions;
        this.activities = activities;
    }

    @Getter
    @Setter
    public static class GroupActivity {
        private LocalDateTime timestamp;
        private String description;

        public GroupActivity(LocalDateTime timestamp, String description) {
            this.timestamp = timestamp;
            this.description = description;
        }
    }
}
