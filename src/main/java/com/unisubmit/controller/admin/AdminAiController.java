package com.unisubmit.controller.admin;

import com.unisubmit.service.ai.EmbeddingBackfillService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-only AI maintenance actions. Route security is enforced by the /admin/** rule in
 * SecurityConfig.
 */
@Controller
@RequestMapping("/admin/ai")
public class AdminAiController {

    private final EmbeddingBackfillService backfillService;

    public AdminAiController(EmbeddingBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    /** Kicks the async embedding backfill and returns immediately (progress lands in the logs). */
    @PostMapping("/backfill-embeddings")
    public String backfillEmbeddings(RedirectAttributes ra) {
        backfillService.backfill();
        ra.addFlashAttribute("successMessage", "Backfill started — progress in the logs.");
        return "redirect:/admin/evaluation";
    }
}
