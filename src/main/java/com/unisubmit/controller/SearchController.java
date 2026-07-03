package com.unisubmit.controller;

import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.SearchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Phase 7 — hybrid search page, available to every authenticated role.
 */
@Controller
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal CustomUserDetails userDetails,
                         @RequestParam(required = false) String q,
                         Model model) {
        String query = q == null ? "" : q.trim();
        List<SearchService.SearchHit> hits = query.isEmpty()
                ? List.of()
                : searchService.search(query, userDetails.getUser());
        model.addAttribute("query", query);
        model.addAttribute("hits", hits);
        model.addAttribute("semanticEnabled", searchService.isSemanticEnabled());
        return "search";
    }
}
