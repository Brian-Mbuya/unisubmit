/* ============================================================================
   UniSubmit - global client behaviour
   Small, dependency-free interactions shared by the Thymeleaf pages.
   ============================================================================ */
(function () {
  "use strict";

  const csrf = {
    token: () => document.querySelector('meta[name="_csrf"]')?.getAttribute("content"),
    header: () => document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content"),
  };

  function markActiveNav() {
    const path = window.location.pathname;
    document.querySelectorAll(".nav .nav-link").forEach((link) => {
      const href = link.getAttribute("href");
      if (href && href !== "/" && path.startsWith(href)) {
        link.classList.add("active");
      }
    });
  }

  function initMobileNav() {
    const toggle = document.querySelector(".menu-toggle");
    const nav = document.getElementById("primaryNav");
    if (!toggle || !nav) return;
    toggle.addEventListener("click", () => {
      const open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
    });
  }

  function initInsightPolling() {
    document.querySelectorAll(".insight-polling").forEach((panel) => {
      const id = panel.getAttribute("data-id");
      if (!id) return;
      const interval = setInterval(() => {
        fetch(`/api/ai-insights/${id}`)
          .then((res) => res.json())
          .then((data) => {
            if (data && data.status !== "PROCESSING" && data.status !== "PENDING") {
              clearInterval(interval);
              window.location.reload();
            }
          })
          .catch((err) => console.error("AI insight polling error:", err));
      }, 2000);
    });
  }

  window.retryAnalysis = function retryAnalysis(btn) {
    const id = btn.getAttribute("data-retry-id");
    const token = csrf.token();
    const header = csrf.header();
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Retrying...";
    fetch(`/api/ai-insights/${id}/retry`, {
      method: "POST",
      headers: token && header ? { [header]: token } : {},
    })
      .then((res) => res.json())
      .then((data) => {
        if (data.status === "PENDING") {
          window.location.reload();
        } else {
          btn.disabled = false;
          btn.textContent = original;
          alert("Could not retry: " + (data.message || "unknown error"));
        }
      })
      .catch(() => {
        btn.disabled = false;
        btn.textContent = original;
      });
  };

  function initRetryButtons() {
    document.querySelectorAll("[data-retry-analysis]").forEach((button) => {
      button.addEventListener("click", () => window.retryAnalysis(button));
    });
  }

  function initReviewActions() {
    const form = document.querySelector("[data-review-form]");
    if (!form) return;

    const statusInput = form.querySelector("[data-review-status]");
    const buttons = form.querySelectorAll("[data-review-action]");
    if (!statusInput || buttons.length === 0) return;

    function select(button) {
      statusInput.value = button.getAttribute("data-review-action");
      buttons.forEach((item) => {
        const active = item === button;
        item.classList.toggle("btn-selected", active);
        item.setAttribute("aria-pressed", String(active));
      });
    }

    buttons.forEach((button) => {
      button.addEventListener("click", () => select(button));
    });

    select(form.querySelector("[data-review-action='UNDER_REVIEW']") || buttons[0]);
  }

  function setFieldVisible(field, visible) {
    if (field) field.hidden = !visible;
  }

  function syncRoleFields(select) {
    const scope = document.querySelector(select.getAttribute("data-role-scope"));
    if (!scope) return;
    setFieldVisible(scope.querySelector("[data-student-id-field]"), select.value === "STUDENT");
    setFieldVisible(scope.querySelector("[data-staff-id-field]"), select.value === "LECTURER");
  }

  function initAdminForms() {
    document.querySelectorAll("[data-role-select]").forEach((select) => {
      syncRoleFields(select);
      select.addEventListener("change", () => syncRoleFields(select));
    });

    document.querySelectorAll("[data-confirm]").forEach((form) => {
      form.addEventListener("submit", (event) => {
        if (!window.confirm(form.getAttribute("data-confirm"))) {
          event.preventDefault();
        }
      });
    });
  }

  function initTableSearch() {
    document.querySelectorAll("[data-table-search]").forEach((input) => {
      const table = document.querySelector(input.getAttribute("data-table-search"));
      if (!table) return;
      const rows = table.querySelectorAll("tbody tr");
      input.addEventListener("input", () => {
        const query = input.value.trim().toLowerCase();
        rows.forEach((row) => {
          row.hidden = query.length > 0 && !row.textContent.toLowerCase().includes(query);
        });
      });
    });
  }

  function initSubmissionFilters() {
    const rows = document.querySelectorAll(".submission-card");
    const filters = document.querySelectorAll("[data-submission-filter]");
    const list = document.getElementById("submissionsList");
    const empty = document.getElementById("filterEmptyState");
    if (!rows.length || !filters.length || !list || !empty) return;

    filters.forEach((button) => {
      button.addEventListener("click", () => {
        const status = button.getAttribute("data-submission-filter");
        let visible = 0;
        rows.forEach((row) => {
          const show = status === "all" || row.getAttribute("data-status") === status;
          row.hidden = !show;
          if (show) visible += 1;
        });
        list.hidden = visible === 0;
        empty.hidden = visible !== 0;
        filters.forEach((item) => item.classList.remove("active"));
        button.classList.add("active");
      });
    });
  }

  /* AI insight tabs (Summary / Problem / Objectives) — event delegation, so
     it works no matter when or where the fragment is rendered. */
  function initAiTabs() {
    document.addEventListener("click", function (event) {
      const btn = event.target.closest(".ai-tab-btn[data-ai-tab]");
      if (!btn) return;
      event.preventDefault();
      const container = btn.closest(".ai-complete");
      if (!container) return;
      container.querySelectorAll(".ai-tab-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      container.querySelectorAll(".ai-tab-content").forEach((c) => c.classList.remove("active"));
      const target = container.querySelector("#" + btn.dataset.aiTab);
      if (target) target.classList.add("active");
    });
  }

  function initDraftTitleSuggestions() {
    const fileInput = document.getElementById("file");
    const container = document.getElementById("new-title-suggestions-container");
    const spinner = document.getElementById("suggestions-spinner");
    const statusText = document.getElementById("suggestions-status-text");
    const list = document.getElementById("new-title-suggestions-list");
    const titleInput = document.getElementById("title");

    if (!fileInput || !container || !spinner || !statusText || !list || !titleInput) return;

    fileInput.addEventListener("change", () => {
      const file = fileInput.files[0];
      if (!file) return;

      // Show spinner, loading text, and container
      container.style.display = "block";
      spinner.style.display = "inline-block";
      statusText.textContent = "AI is analyzing document and generating title suggestions...";
      list.innerHTML = "";

      const formData = new FormData();
      formData.append("file", file);

      const token = csrf.token();
      const header = csrf.header();

      fetch("/api/ai/analyze-draft-file", {
        method: "POST",
        headers: token && header ? { [header]: token } : {},
        body: formData,
      })
        .then((res) => res.json())
        .then((data) => {
          spinner.style.display = "none";
          if (data.error) {
            statusText.textContent = "⚠️ Could not generate suggestions: " + data.error;
            return;
          }
          if (data.suggestions && data.suggestions.length > 0) {
            // Auto-populate the first suggestion directly into the input field
            const firstSuggestion = data.suggestions[0];
            titleInput.value = firstSuggestion;
            titleInput.style.borderColor = "var(--primary)";
            setTimeout(() => { titleInput.style.borderColor = ""; }, 1500);

            statusText.textContent = "✨ AI auto-populated title. Click an alternative below to swap:";
            data.suggestions.forEach((title, index) => {
              const item = document.createElement("button");
              item.type = "button";
              item.className = "btn btn-secondary btn-sm text-left w-full justify-start py-2 px-3 mt-1";
              item.style.textAlign = "left";
              item.style.whiteSpace = "normal";
              item.style.display = "block";
              item.textContent = `Option ${index + 1}: ${title}`;
              item.addEventListener("click", () => {
                titleInput.value = title;
                titleInput.focus();
                titleInput.style.borderColor = "var(--primary)";
                setTimeout(() => { titleInput.style.borderColor = ""; }, 1500);

                // Make the suggestions disappear smoothly
                container.style.transition = "opacity 0.3s ease, transform 0.3s ease";
                container.style.opacity = "0";
                container.style.transform = "translateY(-5px)";
                setTimeout(() => {
                  container.style.display = "none";
                  container.style.opacity = "";
                  container.style.transform = "";
                }, 300);
              });
              list.appendChild(item);
            });
          } else {
            statusText.textContent = "⚠️ " + (data.message || "No suggestions returned.");
          }
        })
        .catch((err) => {
          spinner.style.display = "none";
          statusText.textContent = "⚠️ Error generating title suggestions.";
          console.error("Error fetching draft title suggestions:", err);
        });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    markActiveNav();
    initMobileNav();
    initInsightPolling();
    initRetryButtons();
    initReviewActions();
    initAdminForms();
    initTableSearch();
    initSubmissionFilters();
    initAiTabs();
    initDraftTitleSuggestions();
  });
})();
