# UniSubmit · ROADMAP V4 — adoptable by a real school

> V3 (`ROADMAP.md`) is **complete** — phases 0–6 all landed. This supersedes it.
> V3 was about *building features*. V4 is about making the thing a real school can adopt and a
> judge can understand in four minutes.
>
> Evidence: **63 findings** from 6 audit/research lenses, full text in
> [`notes/RESEARCH-NOTES.md`](notes/RESEARCH-NOTES.md). This file is the plan; that file is the proof.

---

## ⚠️ §00 · DO THIS TODAY — eligibility

**MLH's standard rules say a team "cannot submit a project that includes prior work."** Reusing
an *idea* is fine; reusing **code** is not. UniSubmit is 181 Java files, ~17.4k LOC, with months
of history in `ROADMAP-ARCHIVE.md`.

**Action (15 minutes):** read your specific event's rules page and classify it —
(a) MLH standard unmodified, (b) forked/edited, (c) capstone/FYP showcase with no build-window rule.

If (a) or (b): **do not submit UniSubmit as the project.** Scope the entry to what is genuinely
built inside the window — the onboarding layer (Phase 1) is a defensible standalone entry.

Why this is first: organisers run cheating checks on **finalists**, so the failure lands exactly
when you are winning, and it is silent until then. It also decides the demo's scope.
*(Sources: [MLH rules](https://github.com/MLH/mlh-policies/blob/main/standard-hackathon-rules.md), [MLH judging guide](https://guide.mlh.com/general-information/judging-and-submissions/rules-for-your-hackathon))*

---

## §0 · Honest assessment

**Genuinely strong.** Two independent security chains (session + stateless JWT); class-rep as an
additive authority rather than a fourth role; pgvector semantic search degrading gracefully with
no API key; versioned submissions; a branding engine that ports Material You's real scoring
algorithm. 127 tests. ~17.4k LOC.

**Genuinely weak.** Three themes, all confirmed by independent lenses:

1. **The empty room.** No setup wizard exists anywhere. Nine sequential imports in an order the
   import page's own layout contradicts. 17 of 39 templates have no empty state.
2. **Promises the product does not keep.** The admin can promote a class rep — and that student
   then sees *nothing*, because every rep action is JWT-only for a Flutter client that does not
   exist. Password reset tells staff "a code was sent" when mail is unconfigured and it only
   logged it. Marking is one free-text blob but the UI implies structure.
3. **Coursework primitives are missing.** Deadlines are announcements, not objects: no
   due-vs-cutoff split, no late penalty, no extensions. No rubric. No submission receipt.

**In one sentence:** the engineering is real, but the product over-promises at exactly the points
an evaluator touches first.

---

## §0b · Standing guardrails

1. **Single-tenant.** One school per deployment. Multi-tenancy is out (see §6).
2. **No SPA rewrite.** Server-rendered Thymeleaf, minimal JS.
3. **512 MB budget** (`-Xmx384m`). No per-user caches.
4. **Never enable `unisubmit.seed.demo-accounts` outside `local`** — known-credential admin login.
5. **Each task is self-contained** — files named, acceptance criteria stated — because future
   sessions execute these with limited context.

---

## Phase 0 · GROUND — stop lying to the operator (½ day)

| # | Task | Files | Effort |
|---|---|---|---|
| 0.1 | ✅ **DONE** — `deploy/README.md` claimed the app auto-creates `admin`/`lecturer`/`student` with `password123`. False since the demo trio was gated; following it caused a lockout. | `deploy/README.md` | XS |
| 0.2 | Add `ADMIN_INITIAL_PASSWORD` to `deploy/unisubmit.env.example` + `RAILWAY.md`. It exists only in `.env.example`, which a Railway operator never opens. | `deploy/*` | XS |
| 0.3 | **Rewrite `README.md` — it describes a different product.** It is the judging artifact and the first thing a pilot school reads. | `README.md` | S |
| 0.4 | Update `CODEBASE-MAP.md` for the V4 branding surface (`ThemeStylePreset`, `ThemeFontPreset`, `BrandingManifestController`, client-side `deepenTokens`). | `CODEBASE-MAP.md` | XS |

---

## Phase 1 · FIRST RUN — the empty room (2–3 days)

*Highest leverage in the document. Every judge and every pilot hits this in five minutes.*

| # | Task | Sev/Effort | Notes |
|---|---|---|---|
| 1.1 | **Bootstrap admin is unrecoverable.** Username is `admin`, not an email, so `/forgot-password` cannot reach it. Miss the one-time WARN and the deployment is dead. → take an operator email as the username; fail fast at boot when a fresh DB has no `ADMIN_INITIAL_PASSWORD`; also write the generated password to a file. | CRIT / M | `UnisubmitApplication.java:67-103` |
| 1.2 | **Setup checklist on the admin dashboard**, driven entirely by counters already on the model — no new queries. Nine rows, each a tick or a link. Hide when complete. Persistent card, **not** a modal wizard. | CRIT / S | `AdminDashboardController.java:45-70` |
| 1.3 | **Make import order self-evident** — number 1–9, reorder the DOM so card order == required order, label each with its prerequisite. | HIGH / S | `admin/import.html` |
| 1.4 | **Import page keeps its place** — all nine cards always rendered with per-kind ticks; preview inline under its own card instead of replacing the page. | HIGH / M | `AdminImportController` |
| 1.5 | **Per-row import safety is a lie.** `apply()` is `@Transactional` over the whole batch while the UI promises per-row skip. One bad row rolls back everything. → `REQUIRES_NEW` per applier, the pattern `AIInsightProcessingService` already uses. | HIGH / S | `AcademicImportService.java:307` |
| 1.6 | **Wrong-order imports name their fix** — when all rows share one parent-kind error, show one banner + link, not a table of identical errors. | MED / S | |
| 1.7 | **In-app sample data** — admin-only Load/Remove, synthetic accounts with generated passwords, decoupled from `SEED_DEMO_ACCOUNTS`. | MED / M | Needed for 1.11 |
| 1.8 | **Empty-state taxonomy** — one Thymeleaf fragment, four variants, **mandatory CTA slot**; applied to all 17 bare templates. | HIGH / M | |
| 1.9 | **Cold start is ~120s** on a free container — a judge opening your link waits two minutes for an empty room. Warm it or pre-seed. | HIGH / M | |

---

## Phase 2 · KEPT PROMISES — features that exist but cannot be reached (2 days)

*These read as **broken**, not unbuilt. That distinction is the whole phase.*

| # | Task | Sev/Effort |
|---|---|---|
| 2.1 | **Class-rep has no web UI.** Admin promotes a rep; that student signs in and sees nothing, because all five rep actions are JWT-only. → add `/student/class/**` reusing `ClassRepService` verbatim + a nav entry gated on `hasAuthority('CLASS_REP')`. Or hide the promote button. **Shipping a permission with no consumer is worse than shipping neither.** | CRIT / L |
| 2.2 | **Password reset claims success when mail is off.** `EmailService` gates on `spring.mail.username`; unset by default, it logs the code and returns — but the UI still says "a code was sent". → expose `isConfigured()`; when off, fall through to the admin-notify branch students use. Add a startup WARN. | HIGH / S |
| 2.3 | **`APPROVED` is terminal with no exit for any role**; a submission can never be withdrawn, deleted or re-filed. | HIGH / M |
| 2.4 | **AI insights stuck in `PENDING`/`PROCESSING` have no recovery and no reaper.** | HIGH / M |
| 2.5 | **"Request changes" writes `REJECTED`**, and the same state is named four different ways across two templates. | HIGH / S |
| 2.6 | **Blind review is defeated by the queue** the lecturer passes through to reach it. | HIGH / S |
| 2.7 | **A failed submission redirect discards the whole form including the uploaded file** — brutal on a phone connection. | HIGH / S |

---

## Phase 3 · USABILITY & ACCESSIBILITY (2 days)

Measured: **67 of 105** `<label>` elements have no `for`; **12** modals with zero dialog
semantics or focus management; **12** `window.confirm` calls; **16** tables.

| # | Task | Sev/Effort |
|---|---|---|
| 3.1 | Labels get `for`. Visual-only labels are invisible to screen readers — and accessibility is a procurement checkbox for public universities. | HIGH / M |
| 3.2 | Modals: `role="dialog"`, `aria-modal`, focus trap, Esc, focus restore. One shared helper — the open/close code is currently copy-pasted. | HIGH / S |
| 3.3 | Buttons delete their focus outline and replace it with a ~1.9:1 glow — **fails WCAG 2.2 SC 1.4.11**. | HIGH / XS |
| 3.4 | Group member picker: custom combobox, no ARIA, no arrow keys, Enter silently adds the wrong person. | HIGH / M |
| 3.5 | Destructive actions guarded inconsistently — removing a person has no confirmation at all. Prefer **undo** over confirm where reversible. | HIGH / S |
| 3.6 | Tag rename/merge runs through `window.prompt()` asking the admin to read numeric IDs aloud. | HIGH / M |
| 3.7 | Autosave + local draft recovery on long-form textareas. | HIGH / S |
| 3.8 | Determinate progress for bulk import and AI analysis; stop reloading the whole page. | HIGH / M |
| 3.9 | Status badges must not rely on colour alone. | MED / S |
| 3.10 | Responsive tables — extend the existing `.table-stack` pattern to all 16. | MED / M |

---

## Phase 4 · TONAL DESIGN SYSTEM (3–4 days)

**Problem.** ~50 **flat** custom properties, each an arbitrary hex; contrast checked *after the
fact* with a WCAG warning. Nothing structurally prevents an unreadable school.

**Material 3's model** (read from the spec + `material-color-utilities`): from one source colour
generate **tonal palettes** — primary, secondary, tertiary, neutral, neutral-variant, error —
each with tones `0,10,…,100` where the number *is* perceptual L\*. Semantic **roles** then
reference tones (`primary` = tone 40 light / 80 dark). Because tone distance maps to contrast
ratio, tone-10-on-tone-90 is **contrast-safe by construction**.

| # | Step | Effort |
|---|---|---|
| 4.1 | Define ~26 role tokens (not Material's full 50): `primary`/`on-primary`/`primary-container`/`on-primary-container` ×3 families, `surface`, `on-surface`, `surface-variant`, `on-surface-variant`, `surface-container{,-low,-high}`, `outline`, `outline-variant`, `error` set. | S |
| 4.2 | **Generate server-side in Java, not the vendored JS.** The CSS block is already assembled in `BrandingService`, and the tokens must be validated there regardless — so generating in Java means the admin submits **one validated source colour** instead of a 30-token map. Large security simplification, not just tidiness. | L |
| 4.3 | Alias table: `--brand: var(--md-primary)`, `--canvas: var(--md-surface)`, … **This is what makes it incremental** — zero template changes on day one. | M |
| 4.4 | Collapse light/dark into one token set with two tone assignments, replacing "regenerate the palette per mode". | M |
| 4.5 | Migrate templates off aliases in batches, deleting each alias at its last usage. | L |
| 4.6 | Delete the WCAG *warning*; contrast is now structural. Keep a test asserting every role pairing meets 4.5:1 in both assignments. | S |

**Risk:** 4.3 is load-bearing — do it behind a flag and diff screenshots of five pages.

---

## Phase 5 · COURSEWORK PRIMITIVES (validated — was the research gap)

*Now backed by research across Gradescope, Turnitin, Canvas, Moodle, Google Classroom,
GitHub Classroom.*

| # | Task | Sev/Effort |
|---|---|---|
| 5.1 | **Deadlines are announcements, not objects.** No due-vs-cutoff split, no penalty maths, no per-student extension. This is the foundational missing primitive — most other grading features assume it. | CRIT / L |
| 5.2 | **Rubrics.** Marking is one free-text blob + a 0–100 integer, so marks are neither consistent nor explainable. A rubric also gives the existing AI feedback a *schema to fill* — it makes a feature you already built substantially better. | HIGH / L |
| 5.3 | **Comment bank** — lecturers retype the same twenty sentences across 200 students. Cheapest real time-saver here. | HIGH / S |
| 5.4 | **Marking state + held release + bulk publish** — grades currently go live the instant they are saved. | HIGH / M |
| 5.5 | **Originality view.** pgvector similarity already exists for *collaborator discovery*; reframed it is the feature schools most expect. ⚠️ Semantic vectors alone **will accuse the innocent** — pair with lexical overlap and present as "for review", never as a verdict. | HIGH / L |
| 5.6 | **Submission receipt** — students have no portable proof they submitted (and no email to send one to). | HIGH / S |
| 5.7 | **Batch download / offline marking round-trip / non-submitter list.** | HIGH / M |
| 5.8 | **Student dashboard as a prioritised "what's due, what changed, what to do next" feed**, not a wall of status badges. | HIGH / M |
| 5.9 | Anchored feedback — comments that point at a sentence rather than floating beside the document. | MED / L |
| 5.10 | Structured regrade requests — appeals currently happen off-platform with no audit trail. | MED / M |

---

## Phase 6 · POLISH — the cheap wins that read as expensive

| # | Task | Effort |
|---|---|---|
| 6.1 | **Command palette (Ctrl/Cmd-K)** over a 39-screen IA. Cheap server-rendered; enormous demo value. | M |
| 6.2 | **Promote the existing `AuditLog` into a school-wide activity feed** — the trust surface you already paid for. | M |
| 6.3 | Notification rollup + per-type mute before the feed becomes wallpaper. | M |
| 6.4 | Inline row editing on admin CRUD — kill the list-then-separate-form round trip. | M |
| 6.5 | Keyboard triage for the lecturer review queue (J/K/E + `?` help). | M |

---

## §6 · NOT DOING

| Item | Why |
|---|---|
| **Multi-tenancy** | Deferred by the owner. Not a feature but an architecture change: `Organization` entity, request-time tenant resolution, tenant FK across every academic entity, tenant-keyed cache replacing two `volatile` fields. Weeks. One deployment per school works today. |
| **Flutter client** | Server API exists and is tested; the client does not. Phase 2.1 makes class-rep work on the web instead — same value, a fraction of the cost. |
| **SPA rewrite / real-time** | Nothing in 63 findings requires either. |
| **Custom webfonts** | Presets use OS-resident stacks deliberately — page weight and licensing for zero identity gain. |
| **Proctoring, video, enterprise SSO** | Out of scope for one developer. |

---

## §7 · Hackathon demo

**First, settle §00.** If prior work is barred, the entry is Phase 1 alone, demoed as its own thing.

**Judges stack-rank; being *broadly* good scores zero.** Pick one memorable image and build
around it.

1. **The empty room (20s).** Fresh install. "This is what every school starts with."
   *Requires Phase 1 — today this is the weakest beat; after it, the strongest.*
2. **Onboarding (60s).** Upload a real crest → colours extracted **with coverage shown** → pick
   theme, typeface, shape, light/dark → save. Navbar, login, **browser tab**, installed PWA icon
   all become that school's. Show the tab — that detail lands harder than the palette.
3. **Same code, two schools (20s).** Two deployments side by side, unrecognisable as one product.
   **This is the memorable image.** Pre-build both.
4. **Real work (90s).** Submit → review → AI insight → collaborator discovery.
5. **Depth, at the table not on stage (30s).** Two security chains; Material You's scorer ported
   *and measured*: `#3B82F6` (a colour absent from the image) → `#E8792B` (the actual brand), on
   the same crest. 127 tests.

**Have ready:** two branded deployments; a real crest; one-click sample data (1.7); a 2-minute
fallback video; a README with a 60-second local-run path; a warm container (1.9).

**Cut from the demo:** Phase 4 and the dead-code sweep score zero with judges. Do them for the
codebase, never for the pitch.

**Strongest single artifact:** the extraction before/after measurement — evidence you found a
real defect, quantified it, and fixed it. That is what separates first place from "it works".

---

## Log

| Date | Session | What |
|---|---|---|
| 2026-07-31 | Opus 5 | V4 authored. Phase 0.1 landed. First run: 1 of 8 lenses survived a session limit. |
| 2026-07-31 | Opus 5 | Re-ran all lenses in small batches with per-batch persistence. **63 findings**, all 6 lenses complete. Phase 5 validated; §00 eligibility and Phase 2 (orphan features) are new and were not visible before the research. |
