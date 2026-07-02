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

  /* Phase 6 — Explainable Academic Assistant.
     Both calls hit /api/assistant/{id}/... with the CSRF header; the server
     enforces access control and the per-submission hourly rate limit. */
  function initAssistant() {
    const panel = document.querySelector("[data-assistant-panel]");
    if (!panel) return;
    const submissionId = panel.getAttribute("data-assistant-panel");
    const token = csrf.token();
    const header = csrf.header();

    function post(path, body) {
      const headers = { "Content-Type": "application/json" };
      if (token && header) headers[header] = token;
      return fetch(`/api/assistant/${submissionId}/${path}`, {
        method: "POST",
        headers,
        body: body ? JSON.stringify(body) : "{}",
      }).then((res) => {
        if (res.status === 404) {
          return { available: false, text: "The assistant is not available for this submission." };
        }
        return res.json();
      });
    }

    function show(calloutSel, textSel, data) {
      const callout = panel.querySelector(calloutSel);
      const text = panel.querySelector(textSel);
      if (!callout || !text) return;
      callout.hidden = false;
      callout.classList.toggle("assistant-callout-muted", !data.available);
      text.textContent = data.text || "No answer received.";
    }

    const explainBtn = panel.querySelector("[data-assistant-explain]");
    if (explainBtn) {
      explainBtn.addEventListener("click", () => {
        const original = explainBtn.textContent;
        explainBtn.disabled = true;
        explainBtn.textContent = "Thinking...";
        post("explain")
          .then((data) => show("[data-assistant-explanation]", "[data-assistant-explanation-text]", data))
          .catch(() => show("[data-assistant-explanation]", "[data-assistant-explanation-text]",
            { available: false, text: "Something went wrong — please try again." }))
          .finally(() => {
            explainBtn.disabled = false;
            explainBtn.textContent = original;
          });
      });
    }

    const askBtn = panel.querySelector("[data-assistant-ask]");
    const questionInput = panel.querySelector("[data-assistant-question]");
    if (askBtn && questionInput) {
      const ask = () => {
        const question = questionInput.value.trim();
        if (!question) {
          questionInput.focus();
          return;
        }
        const original = askBtn.textContent;
        askBtn.disabled = true;
        askBtn.textContent = "Thinking...";
        post("ask", { question })
          .then((data) => show("[data-assistant-answer]", "[data-assistant-answer-text]", data))
          .catch(() => show("[data-assistant-answer]", "[data-assistant-answer-text]",
            { available: false, text: "Something went wrong — please try again." }))
          .finally(() => {
            askBtn.disabled = false;
            askBtn.textContent = original;
          });
      };
      askBtn.addEventListener("click", ask);
      questionInput.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          ask();
        }
      });
    }
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
    initAssistant();
  });
})();
