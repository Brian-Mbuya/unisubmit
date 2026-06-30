package com.unisubmit.service;

import com.unisubmit.domain.ProjectGroup;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.User;
import com.unisubmit.repository.ProjectGroupRepository;
import com.unisubmit.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.unisubmit.domain.NotificationType;

@Service
public class ProjectGroupService {

    private final ProjectGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.unisubmit.repository.SubmissionRepository submissionRepository;

    public ProjectGroupService(ProjectGroupRepository groupRepository,
                               UserRepository userRepository,
                               NotificationService notificationService,
                               com.unisubmit.repository.SubmissionRepository submissionRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.submissionRepository = submissionRepository;
    }

    /** Creates a new group with the requesting user as leader. */
    @Transactional
    public ProjectGroup createGroup(User leader, String name) {
        ProjectGroup group = new ProjectGroup();
        group.setName(name);
        group.setLeader(leader);
        group.getMembers().add(leader); // Leader is also a member
        return groupRepository.save(group);
    }

    /**
     * Adds a member to the group.
     *
     * Security: only the group leader or an ADMIN may add members.
     */
    @Transactional
    public ProjectGroup addMember(User requestingUser, Long groupId, Long memberId) {
        ProjectGroup group = findGroupById(groupId);
        assertLeaderOrAdmin(requestingUser, group);

        User newMember = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("User not found: " + memberId));

        if (newMember.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Only students can be added as group members");
        }

        if (!group.getMembers().contains(newMember)) {
            group.getMembers().add(newMember);
            notificationService.createNotification(
                    newMember,
                    NotificationType.SYSTEM_NOTICE,
                    "You have been added to the project group '" + group.getName() + "' by " + requestingUser.getName() + ".",
                    null
            );
        }
        return groupRepository.save(group);
    }

    /**
     * Removes a member from the group.
     *
     * Security: only the group leader or an ADMIN may remove members.
     * The leader cannot remove themselves.
     */
    @Transactional
    public ProjectGroup removeMember(User requestingUser, Long groupId, Long memberId) {
        ProjectGroup group = findGroupById(groupId);
        assertLeaderOrAdmin(requestingUser, group);

        if (group.getLeader().getId().equals(memberId)) {
            throw new IllegalArgumentException("The group leader cannot be removed from the group");
        }

        User oldMember = userRepository.findById(memberId).orElse(null);

        group.getMembers().removeIf(m -> m.getId().equals(memberId));
        
        if (oldMember != null) {
            notificationService.createNotification(
                    oldMember,
                    NotificationType.SYSTEM_NOTICE,
                    "You have been removed from the project group '" + group.getName() + "' by " + requestingUser.getName() + ".",
                    null
            );
        }
        
        return groupRepository.save(group);
    }

    public ProjectGroup findGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));
    }

    public List<com.unisubmit.dto.GroupSummaryDto> findGroupsForUser(User user) {
        List<ProjectGroup> groups = groupRepository.findByMembersContaining(user);
        if (groups.isEmpty()) return List.of();

        // Batch-load all submissions for all groups in ONE query (fixes N+1)
        List<Long> groupIds = groups.stream().map(ProjectGroup::getId).collect(java.util.stream.Collectors.toList());
        List<com.unisubmit.domain.Submission> allSubs = submissionRepository.findByProjectGroupIdIn(groupIds);

        // Group submissions by their projectGroup.id for fast lookup
        java.util.Map<Long, List<com.unisubmit.domain.Submission>> subsByGroupId = allSubs.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getProjectGroup().getId()));

        java.util.List<com.unisubmit.dto.GroupSummaryDto> summaries = new java.util.ArrayList<>();

        for (ProjectGroup group : groups) {
            List<com.unisubmit.domain.Submission> subs =
                    subsByGroupId.getOrDefault(group.getId(), List.of());

            // Build activity feed from pre-loaded submissions (no additional queries)
            List<com.unisubmit.dto.GroupSummaryDto.GroupActivity> activities = new java.util.ArrayList<>();
            for (com.unisubmit.domain.Submission sub : subs) {
                activities.add(new com.unisubmit.dto.GroupSummaryDto.GroupActivity(
                        sub.getCreatedAt(),
                        sub.getStudent().getName() + " created submission '" + sub.getTitle() + "'"
                ));
                for (com.unisubmit.domain.SubmissionVersion v : sub.getVersions()) {
                    if (v.getVersionNumber() > 1) {
                        String uploader = v.getUploadedBy() != null ? v.getUploadedBy().getName() : "A member";
                        activities.add(new com.unisubmit.dto.GroupSummaryDto.GroupActivity(
                                v.getUploadedAt(),
                                uploader + " uploaded Version " + v.getVersionNumber() + " of '" + sub.getTitle() + "'"
                        ));
                    }
                }
            }
            activities.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));

            summaries.add(new com.unisubmit.dto.GroupSummaryDto(
                    group.getId(),
                    group.getName(),
                    group.getLeader(),
                    group.getMembers(),
                    subs,
                    activities
            ));
        }
        return summaries;
    }

    /**
     * Returns true if the given username is the leader of the group.
     * Used by {@code @PreAuthorize} on the membership endpoints.
     */
    @Transactional(readOnly = true)
    public boolean isLeader(Long groupId, String username) {
        return groupRepository.findById(groupId)
                .map(group -> group.getLeader().getUsername().equals(username))
                .orElse(false);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertLeaderOrAdmin(User requestingUser, ProjectGroup group) {
        boolean isLeader = group.getLeader().getId().equals(requestingUser.getId());
        boolean isAdmin  = requestingUser.getRole() == Role.ADMIN;
        if (!isLeader && !isAdmin) {
            throw new AccessDeniedException(
                    "Only the group leader or an admin can modify group membership");
        }
    }
}
