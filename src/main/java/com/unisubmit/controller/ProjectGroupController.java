package com.unisubmit.controller;

import com.unisubmit.domain.ProjectGroup;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.ProjectGroupService;
import com.unisubmit.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/groups")
public class ProjectGroupController {

    private final ProjectGroupService groupService;
    private final UserService userService;

    public ProjectGroupController(ProjectGroupService groupService, UserService userService) {
        this.groupService = groupService;
        this.userService = userService;
    }

    @GetMapping
    public String listGroups(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        List<com.unisubmit.dto.GroupSummaryDto> groups = groupService.findGroupsForUser(userDetails.getUser());
        model.addAttribute("groups", groups);
        List<com.unisubmit.domain.User> students = userService.findByRole(com.unisubmit.domain.Role.STUDENT).stream()
                .filter(u -> !u.getId().equals(userDetails.getUser().getId()))
                .toList();
        model.addAttribute("students", students);
        return "student/groups";
    }

    /** Create a new group — current user becomes the leader. */
    @PostMapping
    public String createGroup(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @RequestParam String name,
                              RedirectAttributes redirectAttributes) {
        try {
            groupService.createGroup(userDetails.getUser(), name);
            redirectAttributes.addFlashAttribute("successMessage", "Group '" + name + "' created.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/groups";
    }

    /** Add a member — only leader or admin. */
    @PreAuthorize("hasRole('ADMIN') or @projectGroupService.isLeader(#id, principal.username)")
    @PostMapping("/{id}/members")
    public String addMember(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @PathVariable Long id,
                            @RequestParam(required = false) Long memberId,
                            RedirectAttributes redirectAttributes) {
        // Bug 5: guard against empty hidden input (no student selected from dropdown)
        if (memberId == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please search for and select a student before clicking Add member.");
            return "redirect:/groups";
        }
        try {
            groupService.addMember(userDetails.getUser(), id, memberId);
            redirectAttributes.addFlashAttribute("successMessage", "Member added.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Access denied: only the group leader or an admin can add members.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/groups";
    }

    /** Remove a member — only leader or admin. */
    @PreAuthorize("hasRole('ADMIN') or @projectGroupService.isLeader(#id, principal.username)")
    @PostMapping("/{id}/members/{userId}/remove")
    public String removeMember(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               @PathVariable Long userId,
                               RedirectAttributes redirectAttributes) {
        try {
            groupService.removeMember(userDetails.getUser(), id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Member removed.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Access denied: only the group leader or an admin can remove members.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/groups";
    }
}
