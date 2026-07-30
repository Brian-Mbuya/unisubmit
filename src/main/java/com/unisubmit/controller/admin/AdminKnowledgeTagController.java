package com.unisubmit.controller.admin;

import com.unisubmit.service.KnowledgeTagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/tags")
public class AdminKnowledgeTagController {

    private final KnowledgeTagService tagService;

    public AdminKnowledgeTagController(KnowledgeTagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public String listTags(Model model) {
        model.addAttribute("technologies", tagService.getAllTechnologies());
        model.addAttribute("researchAreas", tagService.getAllResearchAreas());
        model.addAttribute("frameworks", tagService.getAllFrameworks());
        model.addAttribute("databases", tagService.getAllDatabases());
        model.addAttribute("programmingLanguages", tagService.getAllProgrammingLanguages());
        model.addAttribute("skills", tagService.getAllSkills());
        return "admin/tags";
    }

    @PostMapping("/create")
    public String createTag(@RequestParam String category, @RequestParam String name) {
        if (name == null || name.isBlank()) {
            return "redirect:/admin/tags?error=Name cannot be empty";
        }
        switch (category.toUpperCase()) {
            case "TECHNOLOGY": tagService.findOrCreateTechnology(name); break;
            case "RESEARCH_AREA": tagService.findOrCreateResearchArea(name); break;
            case "FRAMEWORK": tagService.findOrCreateFramework(name); break;
            case "DATABASE": tagService.findOrCreateDatabase(name); break;
            case "PROGRAMMING_LANGUAGE": tagService.findOrCreateProgrammingLanguage(name); break;
            case "SKILL": tagService.findOrCreateSkill(name); break;
            default: return "redirect:/admin/tags?error=Invalid category";
        }
        return "redirect:/admin/tags?success=Tag created";
    }

    @PostMapping("/rename")
    public String renameTag(@RequestParam String category, @RequestParam Long id, @RequestParam String newName) {
        if (newName == null || newName.isBlank()) {
            return "redirect:/admin/tags?error=Name cannot be empty";
        }
        switch (category.toUpperCase()) {
            case "TECHNOLOGY": tagService.renameTechnology(id, newName); break;
            case "RESEARCH_AREA": tagService.renameResearchArea(id, newName); break;
            case "FRAMEWORK": tagService.renameFramework(id, newName); break;
            case "DATABASE": tagService.renameDatabase(id, newName); break;
            case "PROGRAMMING_LANGUAGE": tagService.renameProgrammingLanguage(id, newName); break;
            case "SKILL": tagService.renameSkill(id, newName); break;
            default: return "redirect:/admin/tags?error=Invalid category";
        }
        return "redirect:/admin/tags?success=Tag renamed";
    }

    @PostMapping("/merge")
    public String mergeTags(@RequestParam String category, @RequestParam Long sourceId, @RequestParam Long targetId) {
        if (sourceId.equals(targetId)) {
            return "redirect:/admin/tags?error=Cannot merge a tag into itself";
        }
        switch (category.toUpperCase()) {
            case "TECHNOLOGY": tagService.mergeTechnologies(sourceId, targetId); break;
            case "RESEARCH_AREA": tagService.mergeResearchAreas(sourceId, targetId); break;
            case "FRAMEWORK": tagService.mergeFrameworks(sourceId, targetId); break;
            case "DATABASE": tagService.mergeDatabases(sourceId, targetId); break;
            case "PROGRAMMING_LANGUAGE": tagService.mergeProgrammingLanguages(sourceId, targetId); break;
            case "SKILL": tagService.mergeSkills(sourceId, targetId); break;
            default: return "redirect:/admin/tags?error=Invalid category";
        }
        return "redirect:/admin/tags?success=Tags merged successfully";
    }

    @PostMapping("/delete")
    public String deleteTag(@RequestParam String category, @RequestParam Long id) {
        switch (category.toUpperCase()) {
            case "TECHNOLOGY": tagService.deleteTechnology(id); break;
            case "RESEARCH_AREA": tagService.deleteResearchArea(id); break;
            case "FRAMEWORK": tagService.deleteFramework(id); break;
            case "DATABASE": tagService.deleteDatabase(id); break;
            case "PROGRAMMING_LANGUAGE": tagService.deleteProgrammingLanguage(id); break;
            case "SKILL": tagService.deleteSkill(id); break;
            default: return "redirect:/admin/tags?error=Invalid category";
        }
        return "redirect:/admin/tags?success=Tag deleted";
    }
}
