package com.chuka.irir.controller;

import com.chuka.irir.dto.ProjectCreateDto;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectFile;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.AcademicStructureService;
import com.chuka.irir.service.FileStorageService;
import com.chuka.irir.service.ProjectService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Student project submission and viewing controller.
 */
@Controller
@RequestMapping("/student/projects")
public class StudentProjectController {

    private static final Logger logger = LoggerFactory.getLogger(StudentProjectController.class);

    private final ProjectService projectService;
    private final AcademicStructureService academicStructureService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public StudentProjectController(ProjectService projectService,
            AcademicStructureService academicStructureService,
            UserRepository userRepository,
            FileStorageService fileStorageService) {
        this.projectService = projectService;
        this.academicStructureService = academicStructureService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String listProjects(Authentication authentication, Model model) {
        User user = getUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("projects", projectService.getProjectsForStudent(user));
        model.addAttribute("pageTitle", "My Projects");
        return "student/projects/index";
    }

    @GetMapping("/new")
    public String showNewProjectForm(Authentication authentication, Model model) {
        User user = getUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("project", new ProjectCreateDto());
        model.addAttribute("availableUnits", academicStructureService.listUnitSummaries().stream()
                .filter(unit -> unit.lecturerId() != null)
                .toList());
        model.addAttribute("pageTitle", "Upload Project");
        return "student/projects/new";
    }

    @PostMapping
    public String createProject(@Valid @ModelAttribute("project") ProjectCreateDto projectDto,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {
        User user = getUser(authentication);

        if (!hasFiles(projectDto)) {
            bindingResult.rejectValue("files", "error.files", "Please upload at least one file.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("availableUnits", academicStructureService.listUnitSummaries().stream()
                    .filter(unit -> unit.lecturerId() != null)
                    .toList());
            model.addAttribute("pageTitle", "Upload Project");
            return "student/projects/new";
        }

        Project project = projectService.createProject(projectDto, user);
        logger.info("Student {} created project: {} (ID: {})", user.getEmail(), project.getTitle(), project.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Upload successful. Your project is saved as a draft in IRIR. Submit for review when ready.");
        return "redirect:/student/projects/" + project.getId();
    }

    @GetMapping("/{id}/edit")
    public String editProject(@PathVariable("id") Long projectId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Project project = projectService.getProjectForStudent(projectId, user);
        if (project.getStatus() != com.chuka.irir.model.ProjectStatus.DRAFT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only draft projects can be edited.");
            return "redirect:/student/projects/" + project.getId();
        }

        ProjectCreateDto dto = new ProjectCreateDto();
        dto.setTitle(project.getTitle());
        dto.setAbstractText(project.getAbstractText());
        dto.setAcademicYear(project.getAcademicYear());
        dto.setUnitId(project.getUnit() != null ? project.getUnit().getId() : null);
        if (project.getKeywords() != null && !project.getKeywords().isEmpty()) {
            dto.setKeywords(String.join(", ", project.getKeywords()));
        }

        model.addAttribute("user", user);
        model.addAttribute("projectId", project.getId());
        model.addAttribute("project", dto);
        model.addAttribute("availableUnits", academicStructureService.listUnitSummaries().stream()
                .filter(unit -> unit.lecturerId() != null)
                .toList());
        model.addAttribute("pageTitle", "Edit Draft");
        return "student/projects/edit";
    }

    @PostMapping("/{id}")
    public String updateProject(@PathVariable("id") Long projectId,
            @Valid @ModelAttribute("project") ProjectCreateDto projectDto,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {
        User user = getUser(authentication);
        Project project = projectService.getProjectForStudent(projectId, user);

        if (project.getStatus() != com.chuka.irir.model.ProjectStatus.DRAFT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only draft projects can be edited.");
            return "redirect:/student/projects/" + project.getId();
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("projectId", projectId);
            model.addAttribute("availableUnits", academicStructureService.listUnitSummaries().stream()
                    .filter(unit -> unit.lecturerId() != null)
                    .toList());
            model.addAttribute("pageTitle", "Edit Draft");
            return "student/projects/edit";
        }

        projectService.updateDraft(projectDto, projectId, user);
        logger.info("Student {} updated draft project ID: {}", user.getEmail(), projectId);
        redirectAttributes.addFlashAttribute("successMessage", "Draft updated successfully.");
        return "redirect:/student/projects/" + projectId;
    }

    @PostMapping("/{id}/submit")
    public String submitProject(@PathVariable("id") Long projectId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Project project = projectService.getProjectForStudent(projectId, user);
        if (project.getSupervisor() == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Your project cannot be submitted yet. Choose a unit with an assigned lecturer first.");
            return "redirect:/student/projects/" + projectId;
        }
        projectService.submitProject(projectId, user);
        logger.info("Student {} submitted project ID: {} for review", user.getEmail(), projectId);
        redirectAttributes.addFlashAttribute("successMessage", "Project submitted for review.");
        return "redirect:/student/projects/" + projectId;
    }

    @GetMapping("/{id}")
    public String viewProject(@PathVariable("id") Long projectId,
            Authentication authentication,
            Model model) {
        User user = getUser(authentication);
        Project project = projectService.getProjectForStudent(projectId, user);
        List<Project> recommendations = projectService.recommendCollaborators(project, 5);

        model.addAttribute("user", user);
        model.addAttribute("project", project);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("pageTitle", "Project Details");
        return "student/projects/detail";
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long projectId,
            @PathVariable("fileId") Long fileId,
            Authentication authentication) {
        User user = getUser(authentication);
        Project project = projectService.getProjectForStudent(projectId, user);

        List<ProjectFile> files = project.getFiles() == null ? List.of() : project.getFiles();
        Optional<ProjectFile> fileOpt = files.stream()
                .filter(file -> Objects.equals(file.getId(), fileId))
                .findFirst();

        ProjectFile projectFile = fileOpt
                .orElseThrow(() -> new ResourceNotFoundException("ProjectFile", "id", fileId));

        Resource resource = fileStorageService.loadAsResource(projectFile.getStoragePath());

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (projectFile.getFileType() != null && !projectFile.getFileType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(projectFile.getFileType());
            } catch (Exception e) {
                logger.warn("Failed to parse media type for file {}: {}. Defaulting to octet-stream.", 
                        projectFile.getFileName(), projectFile.getFileType());
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFileName(projectFile.getFileName()) + "\"")
                .body(resource);
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResourceNotFoundException("User", "authentication", "current session");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download";
        }
        return fileName.replace("\"", "");
    }

    private boolean hasFiles(ProjectCreateDto dto) {
        if (dto.getFiles() == null) {
            return false;
        }
        for (var file : dto.getFiles()) {
            if (file != null && !file.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
