package com.unisubmit.controller.admin;

import com.unisubmit.service.AnalyticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Phase 7 — the research landscape map (admin analytics).
 */
@Controller
@RequestMapping("/admin/landscape")
public class AdminAnalyticsController {

    /** One colour per cluster index — jade/brass/sage family on dark ink. */
    private static final List<String> CLUSTER_COLORS = List.of(
            "#5fbfab", "#cda660", "#6fc389", "#aaa0d2", "#e07a6b", "#5c9fc3",
            "#d6a657", "#8fb85f");

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public String landscape(Model model) {
        model.addAttribute("landscape", analyticsService.buildLandscape());
        model.addAttribute("clusterColors", CLUSTER_COLORS);
        return "admin/landscape";
    }
}
