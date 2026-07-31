# UniSubmit · ROADMAP V4 — adoptable by a real school

> V3 (`ROADMAP.md`) is **complete** — phases 0–6 all landed. This supersedes it.
> V4's theme is different: V3 was about *building features*. V4 is about making the thing a
> real school can actually adopt, and a judge can actually understand in four minutes.

---

## §0 · Honest assessment

**What is genuinely strong.** The engineering depth is real and unusual for a student project:
two independent security chains (session + stateless JWT), a class-rep authority modelled as an
additive grant rather than a fourth role, pgvector semantic search with graceful degradation
when no API key is present, versioned submissions, and a branding engine that ports Material
You's actual colour-scoring algorithm. 127 tests pass. 181 Java files, ~17.4k LOC.

**What is genuinely weak.** All of that is invisible for the first hour of use, because a fresh
deployment is an empty room with no signage:

- A brand-new admin lands on a dashboard of zeros with **no next step**. There is no setup
  wizard, checklist, or first-run state anywhere in the codebase.
- Becoming usable requires **nine sequential bulk imports** in an order that is stated once, in
  prose, on a page whose own card order contradicts it.
- 17 of 39 templates have no empty-state handling — a new school sees blank tables.
- There is no in-app way to load sample data, so an evaluator cannot see the app populated
  without doing the nine imports first.

**The gap in one sentence:** the app is built for a school that has already finished onboarding,
and there is no onboarding.

**Provenance note.** The findings in Phase 1 come from a completed automated audit of the real
code (10 findings, each carrying `file:line` evidence). The competitive/UX research pass and
the tonal-system scoping agent **did not complete** — they hit a session limit. Phase 4 below
is therefore my own analysis against the code plus the Material 3 specification I did read
directly, not a fresh competitive survey. Treat Phase 5 as unvalidated until that research runs.

---

## §0b · Standing guardrails

1. **Single-tenant.** One school per deployment. Multi-tenancy is explicitly out (see §6).
2. **No SPA rewrite.** Server-rendered Thymeleaf, minimal JS. Every task below respects that.
3. **512 MB budget.** `-Xmx384m` on Railway. No per-user caches, no in-memory image pipelines.
4. **Never re-enable `unisubmit.seed.demo-accounts` outside `local`.** It is a known-credential
   admin login.
5. **Each task is self-contained** — files named, acceptance criteria stated — because future
   sessions execute these with limited context.

---

## Phase 0 · GROUND — truthful docs (½ day)

*Why first: these are wrong statements in the repo. Everything else builds on top of them.*

| # | Task | Files | Effort |
|---|---|---|---|
| 0.1 | ✅ **DONE** — `deploy/README.md` claimed the app auto-creates `admin`/`lecturer`/`student` with `password123`. False since the security fix; corrected to document `ADMIN_INITIAL_PASSWORD` and the one-time generated password. | `deploy/README.md` | XS |
| 0.2 | Add `ADMIN_INITIAL_PASSWORD` to `deploy/unisubmit.env.example` and `deploy/RAILWAY.md`. It currently appears only in `.env.example`, which an operator deploying to Railway never opens. | `deploy/*` | XS |
| 0.3 | Update `CODEBASE-MAP.md` for the V4 branding surface: `ThemeStylePreset`, `ThemeFontPreset`, `BrandingManifestController`, and the fact that `mapTheme` output is extended client-side by `deepenTokens`. | `CODEBASE-MAP.md` | XS |

**Acceptance:** an operator following `deploy/RAILWAY.md` top-to-bottom can log in on first try.

---

## Phase 1 · FIRST RUN — the empty room problem (2–3 days)

*Why before anything else: this is the single highest-leverage phase. Every judge and every
pilot school hits it in the first five minutes, and today it is the worst part of the product.*

### 1.1 · Admin lockout on first boot — **CRITICAL** · M
**Files:** `UnisubmitApplication.java:67-103`, `EmailService.java`, `AuthController.java`

The bootstrap admin's username is `admin`, not an email — so the emailed-code reset at
`/forgot-password` **cannot reach it**. If the operator misses the one-time WARN line, the
deployment is unrecoverable without DB access.

**Do:** accept an operator-supplied email as the bootstrap admin's username (or store an email
on the account) so the existing reset flow works. Fail fast at boot with a clear message when a
fresh DB has neither `ADMIN_INITIAL_PASSWORD` nor an existing admin, instead of generating a
password nobody reads. Also write the generated password to a file under the upload dir.

**Acceptance:** with no env vars set, boot halts with an actionable message. With
`ADMIN_INITIAL_PASSWORD` set, login works and `/forgot-password` can recover the account.

### 1.2 · First-run setup checklist — **CRITICAL** · S
**Files:** `AdminDashboardController.java:45-70`, `admin/dashboard.html`

**Do:** a checklist card on the admin dashboard driven **entirely by counters already on the
model** — no new queries. Nine rows: Branding → Faculties → Departments → Lecturers →
Programmes → Units → Curricula → Students → Enrolments. Each row: done-tick or a direct link to
the step. Hide the card once all nine are non-zero.

**Acceptance:** a fresh admin sees an ordered list of what to do next and can complete setup
without reading docs. **Do not touch** the existing counter queries.

### 1.3 · Make import order self-evident — **HIGH** · S
**Files:** `admin/import.html`, `AcademicImportService.Kind`

The order is stated once in prose while the cards render in a *different* order.

**Do:** number all nine uploads 1–9 in one continuous list; reorder the DOM so card order equals
required order (Students belongs between Curriculum and Teaching assignments; Lecturers between
Departments and Programmes); label each with its prerequisite.

**Acceptance:** DOM order == required order; each card names what must exist first.

### 1.4 · Import page keeps its place — **HIGH** · M
**Files:** `AdminImportController.java`, `admin/import.html`

Today a preview **replaces the whole page**, so after each of nine steps the admin loses all
context and progress.

**Do:** keep all nine cards rendered at all times with a per-kind status tick from counts the
dashboard already has; render the preview inline under its own card instead of replacing the
page.

**Acceptance:** completing step 3 leaves steps 1–9 visible with 1–3 ticked.

### 1.5 · Per-row import safety is a lie — **HIGH** · S
**Files:** `AcademicImportService.java:307`

`apply()` is `@Transactional` over the whole batch, while `admin/import.html:211` promises
"rows that already exist are skipped, not duplicated" and the result object reports per-row
`created/skipped/failed`. One bad row rolls back the entire import, contradicting both.

**Do:** remove `@Transactional` from `apply` and wrap each applier in `REQUIRES_NEW` (or a
`TransactionTemplate`) — the pattern `AIInsightProcessingService` already uses.

**Acceptance:** a 100-row file with one bad row imports 99 and reports 1 failed.

### 1.6 · Wrong-order imports name their fix — **MEDIUM** · S
**Files:** `AcademicImportService.java`, `admin/import.html`

**Do:** when every invalid row shares one parent-kind error, replace the row table with a single
banner — *"None of these rows can be imported yet — import Departments first"* — plus a direct
link. Collapse repeated identical errors into a count.

### 1.7 · In-app sample data — **MEDIUM** · M
**Files:** new admin action, `CollaborationDemoSeeder`, `RichTestDataSeeder`

Today demo content requires three env flags and a redeploy, and rides on the `password123`
trio — so it cannot be used to demo a production instance.

**Do:** admin-only **Load sample data** / **Remove sample data**, seeding synthetic accounts with
*generated* passwords, decoupled from `SEED_DEMO_ACCOUNTS`.

**Acceptance:** an evaluator sees a populated app in one click; removal restores an empty state.

### 1.8 · Empty states everywhere — **HIGH** · M
**Files:** the 17 templates with no empty-state branch

**Do:** every list/table gets a real empty state: one sentence on what belongs here, and a
button to the action that creates the first one.

**Acceptance:** no blank table anywhere on a fresh install.

---

## Phase 2 · CORRECTNESS — things quietly wrong (1 day)

### 2.1 · Bulk-imported students get a null semester — MEDIUM · S
**Files:** `CsvImportService.java`, student template

Semester is never imported, and a blank `programmeCode` silently creates a mis-attached cohort.
**Do:** add `semester` to the template and pass it through; warn in preview when programme is
blank ("N students have no programme — their work will not attach to a curriculum").

### 2.2 · Self-registration is live but impossible — MEDIUM · S
**Files:** `AuthController.java`, `SecurityConfig.java`

`/register` is public from minute zero but cannot succeed until the academic tree exists — with
no message either way. **Do:** gate it behind an admin toggle, default off until at least one
programme exists; validate the admission number against imported `StudentProfile` rows so
registration *claims a roster row* rather than inventing an account.

### 2.3 · Branding cannot be saved if the JS engine fails — LOW · S
**Files:** `AdminBrandingController.java`

**Do:** when `tokensJson` is absent, derive a minimal token block server-side from the three
base colours already posted, and flash *"Saved with a basic palette."* Branding is step zero;
it must not be the step that hard-fails.

---

## Phase 3 · USABILITY (2 days)

Measured on the current tree: **72** inputs with neither a `label` nor `aria-label`; **12**
`window.confirm()` calls; **16** tables (mobile risk).

- **3.1 · Labels and aria** (S) — every input gets a programmatic label. Accessibility is also a
  procurement checkbox for public universities.
- **3.2 · Replace `window.confirm`** (S) — a styled confirm for destructive actions; prefer
  **undo** over confirm where the action is reversible.
- **3.3 · Responsive tables** (M) — the card-stack pattern already used by `.table-stack`,
  applied to all 16.
- **3.4 · Progress for long operations** (M) — file upload, AI analysis and bulk import all
  block with no feedback. Determinate progress where possible, honest spinners otherwise.

---

## Phase 4 · TONAL DESIGN SYSTEM (3–4 days)

*This is the scoped answer to "make the theming genuinely deep."*

**Problem.** `base.css` defines ~50 **flat** custom properties, each an arbitrary hex. Contrast
is checked *after the fact* with a WCAG warning. Two branded schools can both be readable or
both be broken and nothing structurally prevents the latter.

**Material 3's approach** (read directly from the spec and `material-color-utilities`): from one
source colour, generate **tonal palettes** — primary, secondary, tertiary, neutral,
neutral-variant, error — each with fixed **tones** `0,10,20,30,40,50,60,70,80,90,95,99,100`,
where the number *is* perceptual lightness (L\*). Semantic **role** tokens then reference tones
(`primary` = tone 40 light / tone 80 dark; `on-primary` = tone 100 / tone 20). Because tone
distance maps to contrast ratio, a pairing like tone-10-on-tone-90 is **contrast-safe by
construction** rather than by inspection.

### Migration steps

| # | Step | Effort |
|---|---|---|
| 4.1 | Define UniSubmit's role token set — ~26 roles, not Material's full 50. Minimum: `primary`, `on-primary`, `primary-container`, `on-primary-container`, the same four for `secondary` and `tertiary`, `surface`, `on-surface`, `surface-variant`, `on-surface-variant`, `surface-container{,-low,-high}`, `outline`, `outline-variant`, `error`, `on-error`, `error-container`, `on-error-container`. | S |
| 4.2 | Write the tone generator. **Recommendation: server-side Java**, not the vendored JS. Reason: the CSS block is already assembled server-side in `BrandingService.getSanitizedCssBlock()`, the tokens must be validated there regardless, and generating in Java removes the client's ability to submit arbitrary colour values at all — the admin would submit only a *source colour*, collapsing today's 30-token allow-list to one validated hex. Large security simplification. | L |
| 4.3 | Build the old→new alias table. Every current property is redefined in terms of a role: `--brand: var(--md-primary)`, `--canvas: var(--md-surface)`, `--text: var(--md-on-surface)`, and so on. **This is what makes the migration incremental** — no template changes on day one. | M |
| 4.4 | Collapse light/dark into one token set with two tone assignments, replacing the current "regenerate the whole palette per mode" approach. | M |
| 4.5 | Migrate templates off the aliases in batches, deleting each alias as its last usage goes. | L |
| 4.6 | Delete the WCAG *warning* — contrast becomes structural. Keep a test asserting every role pairing meets 4.5:1 at both tone assignments. | S |

**Risk.** 4.3 is the load-bearing step; if the alias table is wrong the whole UI shifts at once.
Do it behind a feature flag and diff screenshots of five representative pages before/after.

---

## Phase 5 · DIFFERENTIATION (unvalidated — research first)

⚠️ **The competitive-research pass did not run.** Do not build from this list until it has.
These are my own hypotheses from reading the code, not findings:

- **Rubric-based grading** — structured criteria instead of free-text feedback. Highest-value
  gap versus Gradescope/Canvas, and it makes the existing AI feedback far more useful because
  it would have a schema to fill.
- **Similarity surfaced as a report** — pgvector already computes it for *collaboration*; the
  same signal reframed as an originality check is nearly free and is the single feature schools
  most expect.
- **Deadline/extension policy** — per-assignment late windows and per-student extensions.
- **Command palette** (`Ctrl-K`) — cheap in server-rendered apps and dramatically improves the
  demo.
- **Activity feed** — "what changed since I last looked", builds trust for lecturers.

**Do this first:** re-run the research workflow (`unisubmit-hackathon-roadmap`, lenses
`competitive-edtech`, `cross-industry-ux`, `hackathon-judging`) when the session limit resets.

---

## §6 · NOT DOING (and why)

| Item | Why not |
|---|---|
| **Multi-tenancy** | Explicitly deferred by the owner. It is not a feature but an architecture change: an `Organization` entity, request-time tenant resolution, a tenant FK on `User` and every academic entity, and a tenant-keyed cache replacing the two `volatile` fields in `BrandingService`. Weeks, not days — and one deployment per school works today. |
| **Flutter app** | The `/api/v1` server side exists and is tested; the client does not. Building it now competes with fixing onboarding, which affects every user. |
| **SPA rewrite** | Nothing in the findings requires it. |
| **Real-time collaboration** | No finding justifies the WebSocket infrastructure. |
| **Custom font hosting** | Every preset uses OS-resident stacks deliberately; webfonts add page weight and a licence question for zero identity gain. |

---

## §7 · Hackathon demo

**Narrative — lead with the problem, not the tech.**

1. **The empty room (30s).** Fresh install. "This is what every school starts with." — *requires
   Phase 1 to be done; today this beat is the weakest moment, after it, it is the strongest.*
2. **Onboarding (60s).** Upload a real university crest → colours extracted with coverage shown
   → pick a theme, typeface, shape, light/dark → save. The app is now visibly *that school's*:
   navbar, login page, browser tab, installed PWA icon. **Show the browser tab.** That detail
   lands harder than the palette.
3. **Same code, different school (20s).** Second deployment, different crest, side by side.
   Unrecognisable as the same product. This is the memorable image — have both pre-built.
4. **The actual work (90s).** Student submits → lecturer reviews → AI insight → collaborator
   discovery via semantic search.
5. **Depth for the technical judge (30s).** Two security chains; Material You's scoring
   algorithm ported and *measured* (`#3B82F6` before → `#E8792B` after, on the same crest);
   127 tests.

**Have ready:** two branded deployments; a real crest; sample data one click away (Phase 1.7); a
2-minute fallback video in case the network fails; `README` with a 60-second local-run path.

**Judges reward evidence over claims.** The strongest single artifact is the extraction
before/after measurement — it shows you found a real defect, quantified it, and fixed it.

---

## Log

| Date | Session | What |
|---|---|---|
| 2026-07-31 | Opus 5 | V4 authored. Phase 0.1 landed. Audit: 10 findings w/ evidence. Research lenses failed on session limit — Phase 5 unvalidated. |
