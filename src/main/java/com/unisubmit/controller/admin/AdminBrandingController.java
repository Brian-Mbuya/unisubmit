package com.unisubmit.controller.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.service.BrandingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/branding")
public class AdminBrandingController {

    private final BrandingService brandingService;
    private final ObjectMapper objectMapper;

    public AdminBrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public String brandingPage(Model model) {
        // Pre-fill the wizard with whatever this deployment already has, so a returning
        // admin edits their school rather than starting from a blank form.
        model.addAttribute("currentSchoolName", brandingService.getSchoolName());
        model.addAttribute("currentLogo", brandingService.getLogoDataUri());
        return "admin/branding";
    }

    /**
     * Wizard save: school name + logo + palette in one submit. {@code schoolName} and
     * {@code logoPng} are optional — omitting the logo keeps the stored one, so renaming
     * does not require re-uploading the file.
     */
    @PostMapping
    public String saveBranding(@RequestParam("tokensJson") String tokensJson,
                               @RequestParam(value = "schoolName", required = false) String schoolName,
                               @RequestParam(value = "logoPng", required = false) String logoPng,
                               RedirectAttributes redirectAttributes) {
        try {
            Map<String, String> tokens = objectMapper.readValue(tokensJson, new TypeReference<Map<String, String>>() {});
            brandingService.saveSchoolIdentity(schoolName, logoPng, tokens);
            String label = (schoolName != null && !schoolName.isBlank()) ? schoolName.trim() : "This deployment";
            redirectAttributes.addFlashAttribute("successMessage", label + " is now branded and live.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update branding: " + e.getMessage());
        }
        return "redirect:/admin/branding";
    }

    @PostMapping("/reset")
    public String resetBranding(RedirectAttributes redirectAttributes) {
        try {
            brandingService.resetToDefault();
            redirectAttributes.addFlashAttribute("successMessage", "University branding reset to default Nocturne Laurel theme.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to reset branding: " + e.getMessage());
        }
        return "redirect:/admin/branding";
    }
}
