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
    document.querySelectorAll(".nav .nav-link, .bottom-nav .bottom-nav__link").forEach((link) => {
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
      // Bounded polling: give up after ~2 min so a stuck-PENDING insight can't
      // poll forever in a backgrounded mobile tab (battery/memory drain, and a
      // contributor to renderer crashes on constrained devices). Stop on error.
      let attempts = 0;
      const MAX_ATTEMPTS = 40;
      const interval = setInterval(() => {
        if (++attempts > MAX_ATTEMPTS) { clearInterval(interval); return; }
        fetch(`/api/ai-insights/${id}`)
          .then((res) => res.json())
          .then((data) => {
            if (data && data.status !== "PROCESSING" && data.status !== "PENDING") {
              clearInterval(interval);
              window.location.reload();
            }
          })
          .catch(() => clearInterval(interval));
      }, 3000);
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

      // Show spinner, loading text, and skeleton rows — never a blank panel
      container.style.display = "block";
      spinner.style.display = "inline-block";
      statusText.textContent = "Suggesting titles…";
      list.innerHTML = "";
      for (let i = 0; i < 3; i++) {
        const bone = document.createElement("div");
        bone.className = "skeleton skeleton-suggestion";
        bone.setAttribute("aria-hidden", "true");
        list.appendChild(bone);
      }

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
          list.innerHTML = ""; // clear skeleton placeholders
          if (data.error) {
            statusText.textContent = "Couldn't suggest titles: " + data.error;
            return;
          }
          if (data.suggestions && data.suggestions.length > 0) {
            // Auto-populate the first suggestion directly into the input field
            const firstSuggestion = data.suggestions[0];
            titleInput.value = firstSuggestion;
            titleInput.style.borderColor = "var(--primary)";
            setTimeout(() => { titleInput.style.borderColor = ""; }, 1500);

            statusText.textContent = "Suggested titles";
            data.suggestions.forEach((title) => {
              const item = document.createElement("button");
              item.type = "button";
              item.className = "btn btn-secondary btn-sm text-left w-full justify-start py-2 px-3 mt-1";
              item.style.textAlign = "left";
              item.style.whiteSpace = "normal";
              item.style.display = "block";
              item.textContent = title;
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
            statusText.textContent = data.message || "No suggestions.";
          }
        })
        .catch((err) => {
          spinner.style.display = "none";
          list.innerHTML = ""; // clear skeleton placeholders
          statusText.textContent = "Couldn't suggest titles.";
          console.error("Error fetching draft title suggestions:", err);
        });
    });
  }

  /* Submit feedback: any form marked data-loading-submit disables its submit
     button once the submit is really going ahead and swaps the label for a
     small spinner, so slow networks never look frozen. The attribute value is
     the in-flight label (falls back to "Working…"). Additive — no other hook
     in this file is touched. */
  function initLoadingSubmit() {
    const forms = document.querySelectorAll("form[data-loading-submit]");
    if (!forms.length) return;

    forms.forEach((form) => {
      form.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return; // page validation cancelled it
        const btn = form.querySelector('button[type="submit"], input[type="submit"]');
        if (!btn || btn.disabled || btn.getAttribute("aria-busy") === "true") return;
        // Spinner applies IMMEDIATELY so even a fast submit shows feedback.
        btn.dataset.restoreLabel = btn.innerHTML;
        btn.setAttribute("aria-busy", "true");
        const label = form.getAttribute("data-loading-submit") || "Working…";
        btn.textContent = "";
        const spinner = document.createElement("span");
        spinner.className = "ai-spinner";
        spinner.setAttribute("aria-hidden", "true");
        btn.appendChild(spinner);
        btn.appendChild(document.createTextNode(" " + label));
        // Only the disable is deferred one tick, so the submission (and the
        // button's own value, if it has a name) is fully captured first.
        window.setTimeout(() => { btn.disabled = true; }, 0);
      });
    });

    // Coming back via the back/forward cache must not leave a dead button.
    window.addEventListener("pageshow", (event) => {
      if (!event.persisted) return;
      document.querySelectorAll('form[data-loading-submit] [aria-busy="true"]').forEach((btn) => {
        btn.disabled = false;
        btn.removeAttribute("aria-busy");
        if (btn.dataset.restoreLabel != null) {
          btn.innerHTML = btn.dataset.restoreLabel;
          delete btn.dataset.restoreLabel;
        }
      });
    });
  }

  /* GitHub-style navigation feedback: a thin indeterminate bar fixed to the
     very top of the viewport while a full page navigation is in flight.
     Additive — shows on same-origin link clicks and on form submits that do
     not already show a button spinner ([data-loading-submit]). Cleared on
     pagehide/pageshow so a bfcache-restored page never keeps a stale bar. */
  function initNavProgress() {
    let bar = null;
    let safetyTimer = null;

    function show() {
      if (bar) return;
      bar = document.createElement("div");
      bar.className = "nav-progress";
      bar.setAttribute("role", "progressbar");
      bar.setAttribute("aria-label", "Loading page");
      const fill = document.createElement("span");
      fill.className = "nav-progress-fill";
      bar.appendChild(fill);
      document.body.appendChild(bar);
      // Safety: if the navigation never happens (cancelled, download without
      // a download attribute, offline), don't leave the bar sweeping forever.
      safetyTimer = window.setTimeout(hide, 15000);
    }

    function hide() {
      if (safetyTimer) { window.clearTimeout(safetyTimer); safetyTimer = null; }
      if (bar) { bar.remove(); bar = null; }
    }

    document.addEventListener("click", (event) => {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const link = event.target.closest ? event.target.closest("a[href]") : null;
      if (!link) return;
      if (link.target && link.target !== "_self") return; // new tab/window
      if (link.hasAttribute("download")) return;
      const href = link.getAttribute("href");
      if (!href || href.charAt(0) === "#") return;
      let url;
      try { url = new URL(link.href, window.location.href); } catch (e) { return; }
      if (url.origin !== window.location.origin) return;
      if (url.protocol !== "http:" && url.protocol !== "https:") return;
      // In-page jump: same path + query, only the hash differs
      if (url.pathname === window.location.pathname &&
          url.search === window.location.search && url.hash) return;
      show();
    });

    document.addEventListener("submit", (event) => {
      if (event.defaultPrevented) return;
      const form = event.target;
      if (!form || form.nodeName !== "FORM") return;
      if (form.hasAttribute("data-loading-submit")) return; // button spinner covers it
      if (form.target && form.target !== "_self") return;
      show();
    });

    window.addEventListener("pagehide", hide);
    window.addEventListener("pageshow", hide);
  }

  /* Installable app: register the service worker (served from the origin root
     so its scope covers the whole app). Fails silently on old browsers. */
  function initServiceWorker() {
    if (!("serviceWorker" in navigator)) return;
    navigator.serviceWorker
      .register("/sw.js")
      .catch((err) => console.warn("Service worker registration failed:", err));
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
    initLoadingSubmit();
    initNavProgress();
    initServiceWorker();
  });
})();
