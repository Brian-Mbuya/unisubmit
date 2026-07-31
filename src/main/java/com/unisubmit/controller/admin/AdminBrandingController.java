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
        model.addAttribute("currentStyle", brandingService.getThemeStyle());
        // Without these the colour pickers fall back to the stock defaults, and a
        // rename-only save would post a stock palette and silently revert the school.
        Map<String, String> base = brandingService.getBaseColors();
        model.addAttribute("basePrimary", base.getOrDefault("primary", "#35a08c"));
        model.addAttribute("baseSecondary", base.getOrDefault("secondary", "#cda660"));
        model.addAttribute("baseNeutral", base.getOrDefault("neutral", "#1a1d21"));
        model.addAttribute("stylePresets", com.unisubmit.domain.ThemeStylePreset.values());
        model.addAttribute("currentFont", brandingService.getFontStyle());
        model.addAttribute("fontPresets", com.unisubmit.domain.ThemeFontPreset.values());
        model.addAttribute("currentDarkMode", brandingService.isDarkMode());
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
                               @RequestParam(value = "themeStyle", required = false) String themeStyle,
                               @RequestParam(value = "fontStyle", required = false) String fontStyle,
                               @RequestParam(value = "darkMode", required = false) Boolean darkMode,
                               @RequestParam(value = "basePrimary", required = false) String basePrimary,
                               @RequestParam(value = "baseSecondary", required = false) String baseSecondary,
                               @RequestParam(value = "baseNeutral", required = false) String baseNeutral,
                               RedirectAttributes redirectAttributes) {
        try {
            if (tokensJson == null || tokensJson.isBlank()) {
                // The browser posts this empty when theme generation failed. Jackson's
                // own "end-of-input" message is meaningless to an admin, so name the
                // actual problem.
                throw new IllegalArgumentException(
                        "No theme was generated in the browser — reload the page and try again.");
            }
            Map<String, String> tokens = objectMapper.readValue(tokensJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> baseColors = new java.util.LinkedHashMap<>();
            if (basePrimary != null) baseColors.put("primary", basePrimary);
            if (baseSecondary != null) baseColors.put("secondary", baseSecondary);
            if (baseNeutral != null) baseColors.put("neutral", baseNeutral);
            brandingService.saveSchoolIdentity(schoolName, logoPng, themeStyle, fontStyle,
                    darkMode, baseColors, tokens);
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
