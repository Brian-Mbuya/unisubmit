package com.unisubmit.controller.admin;

import com.unisubmit.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 7 — admin-only view of the recommendation evaluation harness.
 * Route security is enforced by the /admin/** rule in SecurityConfig.
 */
@Controller
@RequestMapping("/admin/evaluation")
public class AdminEvaluationController {

    private final EvaluationService evaluationService;

    public AdminEvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public String evaluation(Model model) {
        EvaluationService.EvaluationReport report = evaluationService.evaluate();
        model.addAttribute("report", report);
        model.addAttribute("collab", evaluationService.collaborationStats());
        return "admin/evaluation";
    }
}
