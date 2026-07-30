# UniSubmit → Fable 5 · Design & Optimization Handoff

> **You are Fable 5**, brought onto a small studio team as **design lead + front-end engineer**.
> Your mandate: transform how UniSubmit *feels* for two very specific audiences, harden it against
> errors, and make it excellent on a phone — **without breaking a product that is already live in
> production.** Ship incrementally; keep it working at every step.
>
> Live now: **https://unisubmit-production-d55b.up.railway.app** · every push to `main` auto-deploys.

---

## 1 · The product in one breath

UniSubmit is a university project-submission platform. A **student** uploads a document → the app
extracts the text (Apache Tika) and produces an AI/heuristic **insight** (summary, topics, tags) →
a **lecturer** reviews and grades it in a split document/feedback view → the engine **matches**
similar projects and **surfaces collaborators**. There's an **admin** area for the academic
structure (faculties, departments, programmes, units, curricula, assignments).

**Stack (do not re-platform):** Spring Boot 4 / Java 17 · server-rendered **Thymeleaf** · Spring
Security · JPA/Hibernate + Flyway · **Supabase** Postgres · one design-system stylesheet
(`static/css/base.css`) · one vanilla JS file (`static/js/app.js`). No SPA, no build step for the
front end. You work inside Thymeleaf templates + `base.css` + `app.js`.

---

## 2 · Who we are designing for — the north star

Everything you decide gets checked against these two people:

**The student — busy and impatient.** Avoids anything that looks complex or wordy; abandons a task
the moment the next step isn't obvious; lives on a **phone**. If submitting takes more than a few
taps or the screen is a wall of text, they bounce.

**The lecturer — tired and tech-wary.** Not excited by "new tech"; wants calm, familiar, reassuring
screens with plain language and no jargon; needs to feel confident they can't break anything.

**Design principles that follow from this (your rubric):**
1. **One obvious action per screen.** The primary thing to do is the biggest, clearest element.
2. **Fewer words.** Cut eyebrows, subtitles, and hints down to what's load-bearing. Plain English over academic register.
3. **Mobile-first, thumb-first.** Design the 360–414px phone view first, then scale up. Tap targets ≥ 44px.
4. **Progressive disclosure.** Show the essential; tuck the advanced behind "more".
5. **Guiding empty states.** Never a blank panel — always a next step.
6. **Warmth & confidence.** Friendly microcopy, gentle motion, nothing that reads as "developer tool".

---

## 3 · Hard constraints — do not break these

- **It's live.** Prefer incremental, reversible changes. Verify each flow still works before moving on (see §7).
- **The JS ↔ CSS contract is load-bearing.** `app.js` and inline page scripts hook onto specific
  class names, IDs, `data-*` attributes, and endpoints. **Restyle freely in `base.css`; never rename
  a hook.** Full list in §6. `base.css`'s own header says it best: *"Restyle here, never rename there."*
- **CSRF stays.** Every form carries the hidden token (`${_csrf.parameterName}` / `${_csrf.token}`)
  and the layout exposes `meta[name=_csrf]` / `meta[name=_csrf_header]`. Keep them, or POSTs 403.
- **Accessibility is not optional.** Keep the skip-link, ARIA labels, visible focus states, and the
  `prefers-reduced-motion` guard. Improve them where you can.
- **Owner taste, learned from live feedback (respect unless you have a strong, justified reason):**
  - **Low-glare dark.** A bright/"ivory" redesign was explicitly rejected as eye-straining. You *may*
    adjust the palette ("color change where it's just right"), but keep it easy on the eyes — evolve
    the dark, or offer a soft "paper" mode as an *option*, don't force brightness.
  - **Minimal, no feature sprawl.** Removing clutter beats adding UI. (Knowledge-tag UI, an assistant
    card, and keyword chips were all deliberately removed.)
  - **Single-page scroll.** No nested scrollbars — the owner disliked the split-view inner scroll.
  - **Match scores must read intuitively** (identical docs already show 100% "Identical document").

---

## 4 · Honest audit of the current state

**What's already good** — don't throw it away:
- A mature, coherent design system: **"Nocturne Laurel" v3** (`base.css`, ~1950 lines) — deep-ink
  dark surfaces, a **jade** brand (`--primary #5fbfab`, `--brand #2e8f7f`) with a **brass** companion
  (`--gold #cda660`), well-organized tokens, real components (cards, badges, stats, timeline, review
  workspace, AI panel), Inter for UI + **Fraunces** for scholarly display.
- Thoughtful microcopy in places (the submit page's "What happens next" rail is genuinely nice).
- A working responsive baseline: mobile nav collapse, admin sidebar → horizontal scroller, review
  workspace stacks.

**What's holding it back** — your opportunity:
1. **"Whitepage" errors (top priority — reliability).** `GlobalExceptionHandler` only catches
   exceptions thrown *inside controllers*; **Thymeleaf render errors happen after that and slip
   through**, and there is **no custom error page** (`templates/error/` doesn't exist), so any 404/500
   or template null shows Spring's raw **white** Whitelabel Error Page. See §5-A.
2. **Desktop-first & dense.** Base type is **14px** (`0.875rem`), canvas is **1480px**, side-rail is
   **320px**, and there are only 4 breakpoints (1200/1080/860/520). It reads as an information-dense
   admin tool — the exact texture the target audience avoids. Needs air, larger type, and a true
   mobile-first pass.
3. **Fonts load from the Google Fonts CDN** (`layout.html` lines 10–12). External dependency = slow
   first paint on weak phone connections, a flash of unstyled/invisible text, and a single point of
   failure. **Self-host Inter + Fraunces** (or fall back to a strong system stack).
4. **Word density.** Eyebrow + `<h1>` + subtitle + field hints + rail copy stack up on nearly every
   page. Cut hard; keep only what removes doubt.
5. **AI-dependent UI with no key wired.** The live site runs "lean": `OPENAI_API_KEY=NO_KEY`, so
   AI insights fall back to a **local heuristic**, and the submit page's headline feature — *"let the
   AI name it"* (`/api/ai/analyze-draft-file`) — will return no suggestions. Either wire a key or
   **gracefully soften/hide** AI-branded UI when it's unavailable, so users never hit a dead feature.

---

## 5 · Your mission — workstreams, in priority order

### A. Eliminate whitepages *(reliability first — do this before cosmetics)*
- Add **themed error pages**: `templates/error/404.html`, `templates/error/500.html`, and an
  `templates/error/error.html` fallback — in the Nocturne Laurel look, with warm human copy ("This
  page wandered off") and a clear route back (Home / Dashboard). Spring Boot auto-resolves
  `templates/error/<status>.html`.
- **Null-harden templates.** Audit every `${...}` that could be null; use Thymeleaf safe-navigation
  (`?.`) and defaults (`${x} ?: '—'`). Confirm every model attribute the navbar/layout needs is always
  present (`GlobalModelAttributes` supplies `unreadNotificationCount`, `pendingCollaborationCount`,
  `greeting`, `currentFirstName` — anything else a template reads must be guaranteed too).
- Sanity-check the catch-all in `GlobalExceptionHandler` (it redirects to `Referer`) for potential
  redirect loops when the referring page is itself the one erroring.

### B. Mobile-first pass
- Re-derive the type scale and spacing from the phone up; raise the base to ~15–16px; ensure no
  horizontal scroll at 360px; make tap targets ≥ 44px; consider a simplified sticky header (or bottom
  tab bar) for phones so the primary actions are always in thumb reach.
- Pressure-test the dense surfaces on mobile: the lecturer **review split** (`lecturer/review-split.html`
  + `.review-workspace`), admin **tables**, the **stat strips**, and the global search.

### C. Radical simplification of the core flows
- **Submit** (`student/new-submission.html`): make the drop/upload the hero; collapse everything else
  until needed; the primary button unmistakable. Target: a first-timer submits in **under 60s with no
  instructions**.
- **Student dashboard** (`student/dashboard.html`) and **lecturer review queue**
  (`lecturer/dashboard.html`): lead with the one thing they came to do; demote the rest.
- **Findability:** revisit nav labels for plainness ("Review queue", "Collaboration", "Administration"
  → words a tired person parses instantly). Make search obvious on mobile.
- **First-run reassurance** for lecturers: a calm, 3-step "here's how it works" the first time.

### D. Visual / art / feel refresh
- Evolve the identity so it feels **intentional, modern, and calm** — typography, spacing rhythm,
  iconography, tasteful motion (respect reduced-motion), and real **empty-state art/illustration**
  instead of blank panels.
- **Color:** you have latitude to retune the palette "where it's just right." Keep it low-glare (see
  §3). Options worth exploring: warming the jade/brass balance, a softer ink, or an optional dimmed
  "paper" reading mode — but justify any move against the eye-strain constraint and the single-theme
  history. Semantic colors (success/warning/danger) already exist as tokens — keep them distinct from
  the brand accent.

### E. Pipeline / AI / architecture review + suggestions *(document, propose, implement the safe wins)*
- **Current AI reality (lean):** LLM off (`NO_KEY` → heuristic insights), **SPECTER/semantic off**,
  **GROBID off**. Matching therefore runs on keyword + title + unit + technology/research-area signals.
  Make the "why matched" explanation reflect the signals actually available, and design the
  no-AI-key state to look intentional, not broken.
- **Suggestions to raise (prioritized), with your recommendation:** wire an OpenRouter key to unlock
  real summaries/title suggestions; self-host fonts + add long-cache headers on static assets;
  consider `spring.thymeleaf.cache=true` in prod; add DB indexes if any list view is slow; keep the
  existing async **insight polling** UX but add a clear finished/failed state.
- **Operational flags to hand back to the owner (not yours to fix, but name them):** the seeder
  creates `admin/lecturer/student` all `password123` on a public server — **must change**; Supabase
  **RLS** is still to be enabled; Railway's filesystem is **ephemeral** so uploads need a mounted
  Volume at `/app/uploads`.

---

## 6 · The technical contract (reference)

**Key files**
- `templates/layout.html` — base shell (head, viewport, CSRF meta, font links, `#mainContent`, footer).
- `templates/fragments/navbar.html` — top bar: logo, role-aware `#primaryNav`, global search, bell,
  user chip, `.menu-toggle`.
- `templates/fragments/{alerts,components,hierarchy-select}.html` — shared fragments.
- `student/*`, `lecturer/*`, `admin/*`, plus `login/register/forgot-password`, `explore/discover/search/notifications`.
- `static/css/base.css` — the entire design system (25 numbered sections; tokens at top).
- `static/js/app.js` — all client behavior (no framework).

**Load-bearing hooks — restyle, never rename:**
- CSRF: `meta[name="_csrf"]`, `meta[name="_csrf_header"]`, form field `${_csrf.parameterName}`.
- Nav: `.nav .nav-link` (active state), `.menu-toggle`, `#primaryNav`, `.nav.open`.
- AI insight polling: `.insight-polling[data-id]` → `GET /api/ai-insights/{id}`; retry via
  `[data-retry-analysis]` / `data-retry-id` → `POST /api/ai-insights/{id}/retry`; classes
  `.ai-complete`, `.ai-tab-btn[data-ai-tab]`, `.ai-tab-content`.
- Review actions: `[data-review-form]`, `[data-review-status]`, `[data-review-action]`, `.btn-selected`
  (statuses include `UNDER_REVIEW`).
- Submission filters: `.submission-card[data-status]`, `[data-submission-filter]`, `#submissionsList`,
  `#filterEmptyState`, `.active`; admin: `[data-role-select]`, `[data-role-scope]`,
  `[data-student-id-field]`, `[data-staff-id-field]`, `[data-confirm]`, `[data-table-search]`.
- Draft-title AI: `#file`, `#title`, `#new-title-suggestions-container`, `#suggestions-spinner`,
  `#suggestions-status-text`, `#new-title-suggestions-list` → `POST /api/ai/analyze-draft-file`.

**Design tokens (current):** brand jade `--primary #5fbfab` / `--brand #2e8f7f`; brass `--gold #cda660`;
canvas `#121417`, surface `#1a1d21`; text `#eae7e0` / muted `#a8a399`; danger `#e07a6b`, success
`#6fc389`. Type: Inter (UI) + Fraunces (display), body `0.875rem`. Radii 4/6/10/pill. Header 56px,
canvas 1480px, rail 320px. Breakpoints 1200/1080/860/520. `color-scheme: dark`.

**Run & verify**
- Build: set `JAVA_HOME` to `~/.jdks/jdk-17.0.19+10`, then `./mvnw -B -DskipTests package`.
- Local run (H2, no cloud): `run-local.ps1 -Port 8090` → seeds `admin` / `L001` / `S001`, all
  `password123`. Browse `http://localhost:8090`.
- Verify visually in the browser preview: log in per role, walk **login → submit → review → match →
  admin → notifications → groups**, and check each at 360px width. Watch the console/logs for template
  errors. Ship via `git push` to `main` (Railway auto-deploys); DB (Supabase) is untouched by deploys.

---

## 7 · Definition of done

- **No white pages anywhere** — every error is a themed, human page; templates are null-safe.
- **Great on a 360px phone** — no sideways scroll, comfortable type, thumb-friendly targets.
- A first-time **student submits in under 60s** with zero instructions; a **lecturer reviews** without
  needing help or feeling "techy".
- The visual identity feels **deliberate and calm**; any color change is justified and low-glare.
- **Every existing flow still works** and **every JS hook + CSRF token is intact** (§6).
- The **AI/architecture review is written up** with prioritized recommendations; safe wins implemented.

## 8 · Suggested sequencing

1. **Reliability** — error pages + template null-hardening.
2. **Mobile-first shell** — type scale, spacing, header/nav, no-overflow pass.
3. **Flow simplification** — submit, dashboards, review queue, findability.
4. **Visual & color refresh** — identity, motion, empty-state art, palette tuning.
5. **AI / architecture** — no-key states, self-hosted fonts, caching, and the written suggestions.

*Keep the product shippable at the end of every phase.*

---

## 9 · Power Moves — stretch scope (prioritized)

Beyond cleanup, these are the high-leverage things you (Fable) are uniquely good at. They are
ranked for **impact-per-hour** given a limited working window — do them top-down.

**★ P1 · Whitepage kill + skeletons & motion** *(reliability AND delight in one pass)*
- Themed `templates/error/404.html`, `500.html`, `error.html` (warm copy, a way home).
- Skeleton loaders / loading states so a screen is never blank while data or AI is working; a
  satisfying "submission received" moment; tasteful, `prefers-reduced-motion`-safe transitions.
- Done-when: no raw white page anywhere; every wait shows motion, not a blank.

**★ P2 · Mobile-first + installable (PWA)** *(students live on phones — biggest perception jump)*
- True mobile-first pass (base type ↑, no horizontal scroll at 360px, ≥44px targets).
- Add a web app **manifest**, self-hosted icons, and a small **service worker** (offline shell +
  installability) wired into `layout.html`. Result: "Add to Home Screen" feels like a real app.
- Done-when: installable on Android/iOS; passes a Lighthouse PWA/mobile check; no overflow at 360px.

**★ P3 · Voice & microcopy pass** *(fast, transforms the whole feel)*
- Rewrite labels, hints, empty states, and error messages into warm, plain, encouraging language for
  impatient students and tech-wary lecturers. Errors reassure and say what to do next.

**★ P4 · Make matching shine** *(your differentiator)*
- Turn "why matched" into a visual: relationship/similarity map, signal chips, a collaboration
  "pitch card." Reflect only the signals actually available (semantic/LLM may be off). Related:
  `admin/landscape.html`, the recommendation components (`.signal-row`, match cards).

**Also valuable (owner's call, keep minimal):** data-viz for the existing stats (grade
distributions, review queue, admin analytics); a proper brand/logo + favicon set + OG share images;
onboarding/first-run for lecturers; accessibility to AA; designed deadline/feedback emails
(there's a `DeadlineReminderScheduler` already). **Not** a good use of a short window: backend/
architecture rewrites — leave those as the notes in §5-E for the owner.

*Self-host any new fonts/icons/assets — the app must not depend on external CDNs.*
