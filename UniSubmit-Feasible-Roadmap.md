# UniSubmit — Feasible Roadmap (Solo-Buildable Edition)

> Status as of **3 July 2026**: ALL PHASES COMPLETE (1–7). Full UI redesign shipped
> ("Nocturne Laurel" dark theme). Phase 7 delivery notes:
> (1) Evaluation harness — /admin/evaluation, precision@5 + MRR over accepted
>     collaboration requests as ground truth, 6 weight configs compared live.
> (2) Hybrid search — /search for all roles: BM25 keyword ranking (works on H2) +
>     optional pgvector semantic channel (unisubmit.search.semantic-enabled, Postgres
>     only), fused with Reciprocal Rank Fusion; visibility-filtered.
> (3) Near-duplicate integrity check — SHA-256 content hashes (V17), identical files
>     score 100% with an "Identical document" label.
> (4) Research landscape — /admin/landscape, plain-Java k-means + PCA over embeddings
>     (fallback: keyword/tag vectors on H2), SVG dot map with cluster legends.
> (5) BM25 upgrade applied to search retrieval (recommender's keyword signal kept
>     as-is deliberately — owner had just approved its behaviour).
> (6) OCR fallback — unisubmit.ocr.* config + /ocr route in specter-service
>     (pytesseract; returns 501 when deps missing so Java degrades cleanly);
>     triggers when Tika extracts <200 chars.
> (7) Blind review — unisubmit.review.blind-mode flag; identity hidden until the
>     first graded feedback.
> Phase 6 assistant exists as guarded API endpoints only (/api/assistant/*) — UI
> removed at owner's request. Other owner-driven changes: knowledge-tag UI removed
> everywhere (engine still uses LLM tags internally; /admin/tags unlinked but works),
> adaptive score normalisation, suspension enforcement (login block + live-session
> eject), admin programme edit fix, announcements include submitted-to units,
> lecturer doc preview rebuilt, login brute-force throttling. 30 unit tests green.
> **Next**: Phase 8 — AI-Powered Collaboration Discovery (specced + design-reviewed,
> amendments folded in, not yet started), then Phase 9 — Hardening & Completion.
> This file is the handoff: a fresh agent needs only the Current State Snapshot below
> plus a phase's starter prompt. The owner does all manual/browser testing.

This roadmap keeps only what one student, working alone on the current Spring Boot
codebase, can realistically build and demo. Each phase is self-contained: goal, tasks,
and a ready-to-paste starter prompt — so any AI model or agent can pick up a phase in a
fresh chat with zero prior conversation context. **Always paste the "Current State
Snapshot" section below together with the phase's starter prompt.**

---

## Current State Snapshot (paste this with any starter prompt)

> Updated 3 Jul 2026 for handoff to the next agent. Read this whole block before
> touching anything — it reflects the machine and codebase as they are NOW.

```
UniSubmit is a Spring Boot 4 / Java 17 / Thymeleaf / PostgreSQL academic submission
platform at C:\Users\mbuya\OneDrive\Desktop\unisubmit-main (1)\unisubmit-main\unisubmit-main
(NOT a git repo on this machine — no commits, no branches; edit files directly).

TOOLCHAIN (this machine has no system Java/Maven): portable Temurin JDK 17 lives at
C:\Users\mbuya\.jdks\jdk-17.0.19+10. Set JAVA_HOME to it before any mvnw.cmd call:
  $env:JAVA_HOME = "C:\Users\mbuya\.jdks\jdk-17.0.19+10"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Compile check: .\mvnw.cmd -q compile -DskipTests   (must exit 0)
Unit tests:    .\mvnw.cmd test                      (30 green as of handoff)
Run the app:   .\run-local.ps1  (finds the JDK itself; H2 file DB, port 8080,
local profile: Flyway OFF, ddl-auto update). Seeded logins: admin / L001 (lecturer,
"Dr. Smith") / S001 (student, username "student"), all password123. The bash ./mvnw
script is broken; always use mvnw.cmd. A .claude/launch.json config named
"unisubmit-local" starts the app for browser preview.

OWNER'S WORKING AGREEMENT (learned over many feedback rounds — do not relitigate):
- The owner does ALL manual/browser testing personally. Your job ends at: compile
  clean, unit tests green, app running on :8080. Report what to test, then stop.
- Dark theme ONLY ("Nocturne Laurel" in base.css: deep ink surfaces, jade laurel
  primary, brass gold accent, Inter + Fraunces). A light theme was shipped once and
  rejected as eye-straining. :root has color-scheme: dark (needed for native pickers).
- Minimal UI. Removed at the owner's request and MUST NOT be re-added: knowledge-tag
  cards/editors on student+lecturer pages (admin /admin/tags still works, unlinked),
  the Phase 6 assistant UI (endpoints /api/assistant/* remain, backend-only),
  keyword chips in match cards. Before adding ANY visible UI ask whether it serves
  the submit→review→match flow; prefer removing over collapsing.
- One natural page scroll — no nested scrollbars (review page doc panel is sticky).
- Match scores must feel intuitive: identical documents = 100% "Identical document"
  (SHA-256 contentHash on SubmissionVersion, V17), adaptive normalisation excludes
  unfireable signals (no embeddings / no keywords) from the denominator.

DOMAIN (src/main/java/com/unisubmit/domain): User (STUDENT/LECTURER/ADMIN, suspended
flag enforced at login AND per-request by security/SuspensionEnforcementFilter) with
StudentProfile/LecturerProfile, hierarchy Faculty→Department→Programme→Unit→Curriculum,
AcademicYear/Semester, TeachingAssignment (lecturer↔curriculum), Enrollment,
Submission (student, curriculum, projectGroup, supervisors, status enum
DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/PROPOSAL/FINAL/ARCHIVED,
float[] embedding via VectorConverter, ManyToMany tag sets), SubmissionVersion
(+contentHash SHA-256, +Feedback with optional grade, changesSummary column exists but
is UNUSED — Phase 9 item), AIInsight (summary/keywords/objectives/problemStatement,
PENDING/PROCESSING/COMPLETED/FAILED), SubmissionSimilarity (score + six-signal
breakdown + matched lists), SubmissionRelation, AuditLog (append-only, submission
events only), Announcement (deadline recomputed as nearest-future on create/delete),
AppNotification, Collaboration/CollaborationRequest (accepted = evaluation ground
truth), ProjectGroup, Reference, lookup entities Technology/ResearchArea/Framework/
Database/ProgrammingLanguage/Skill (LLM-populated; no student/lecturer editing UI).

AI PIPELINE (service/AIInsightProcessingService): Tika → optional GROBID → LLM call
to an OpenAI-compatible endpoint (OPENAI_API_KEY / OPENAI_BASE_URL, default
OpenRouter, model default openai/gpt-4o-mini; NO key configured locally → honest
fallback: TF keywords + extractive summary, NO fabricated tags) → maps tech/areas
to lookup tables → optional SPECTER2 embedding (specter-service/ Flask sidecar,
SPECTER_ENABLED, :5001; also has /ocr route for scanned PDFs, unisubmit.ocr.*,
default off) → RecommendationService.precomputeForSubmission(). Transaction shape:
short PROCESSING tx → pipeline in its own tx → short FAILED tx; timeout
unisubmit.ai.timeout-seconds (120).

RECOMMENDATION ENGINE (service/RecommendationService): six signals (keyword, title,
unit proximity, semantic cosine, technology Jaccard, research-area Jaccard), weights
under unisubmit.recommendation.weight.*, ADAPTIVE normalisation (unfireable signals
leave the denominator), identical-document override → score 1.0. scorePair() is the
single scoring source; rankCandidateIds(submission, weights) gives live what-if
rankings for the evaluation harness. Startup: config/RecommendationRefreshRunner
(local profile: unisubmit.recommendation.refresh-on-startup=true) backfills content
hashes + recomputes all similarity rows. Read side findSimilarSubmissions(sub, viewer)
re-checks canDiscoverSubmission per viewer. LecturerRecommendationService = reviewer
matching from feedback history (no LLM). KNOWN OWNER COMPLAINT (→ Phase 8): same-unit
proximity creates noise matches ("Same unit: Yes" with all content signals 0%);
collaboration discovery must NOT reward same-unit.

PHASE 7 FEATURES (all live): /admin/evaluation (precision@5 + MRR over accepted
collaboration requests, 6 weight configs, service/EvaluationService), /search for all
roles (BM25 keyword ranking everywhere + optional pgvector semantic channel behind
unisubmit.search.semantic-enabled, RRF fusion, service/SearchService), /admin/landscape
(k-means + power-iteration PCA over embeddings or keyword/tag fallback vectors, SVG
dot map, service/AnalyticsService), OCR fallback trigger (<200 chars extracted),
blind review (unisubmit.review.blind-mode, identity hidden until first graded
feedback). Login throttling: security/LoginAttemptService (5 fails per user+IP →
15 min lock, /login?locked).

UI: server-rendered Thymeleaf; ALL styling tokens in static/css/base.css — restyle
there, never in markup. Fragments in templates/fragments/components.html:
statusBadge, feedbackTimeline(versions, maskUploaders), aiInsightPanel (tabs via
data-ai-tab + delegated JS in app.js), similarProjectsPanel(sims, requestStatuses,
readOnly), lecturerMatchesPanel. Lecturer review-split: sticky doc-preview panel
(PDF iframe / fetched+typeset TXT / download fallback), review action on top, whole
page scrolls. app.js is dependency-free (nav, AI polling, review actions, filters,
AI tabs, table search).

MIGRATIONS: V2–V17 (V16 breakdown, V17 content_hash). Every schema change ships a
V18+ Flyway migration for Postgres AND relies on H2 ddl-auto locally.

RULES FOR EVERY CHANGE: never break existing flows; new entity fields nullable or
defaulted; match existing Lombok/JPA/Thymeleaf patterns; template property paths must
exist on the entity (a bad SpEL path truncates the page mid-render — this bug class
happened; Phase 9 adds rendering smoke tests); finish with mvnw.cmd compile (exit 0)
+ unit tests green; leave the app running and hand the manual test list to the owner.
```

---

## Phase 1 — Academic Foundation — ✅ DONE

Shipped: Faculty/Department/Programme/Unit/Curriculum hierarchy, AcademicYear/Semester,
ProjectGroup with leader+members, multiple supervisors per submission, extended
lifecycle statuses, AppNotification table + bell with unread badge, cascading
hierarchy selector on new-submission, breadcrumbs on detail pages.

## Phase 2 — Knowledge Model — ✅ DONE

Shipped: Technology/ResearchArea/Framework/Database/ProgrammingLanguage/Skill lookup
entities with ManyToMany joins to Submission, Reference entity for citations, seed data
(migrations V9–V11 + CommandLineRunner seeder), admin tag-management console
(admin/tags.html), chip display on submission detail.

## Phase 3 — Academic Intelligence Engine — ✅ DONE (implementation differs from plan)

Shipped: real LLM structured-output analysis replacing keyword-frequency "AI". Note the
deviations from the original plan: uses an **OpenAI-compatible client (OpenRouter)**
instead of spring-ai-anthropic; adds **GROBID** structured PDF parsing (front-matter
problem solved properly — introduction/methodology/conclusion sections are extracted,
with 400-word trim as Tika fallback); adds **SPECTER2 embeddings + pgvector** (V14).
File size (25MB) + MIME/extension allowlist validation exists in FileStorageService.
Loading skeleton + retry button exist. Structured fields (objectives, problem statement)
shown as tabbed sections.

## Phase 4 — Academic Memory — ✅ DONE

Shipped: typed AIInsight fields (objectives, problemStatement), SubmissionRelation
lineage links with staff-only management UI, append-only AuditLog (V15) with vertical
timeline on project-detail, "Related projects" section kept distinct from automatic
"Similar work".

---

## Phase 5 — Recommendation Engine — ✅ DONE

**Already shipped (do not redo):**
- Six-signal weighted scoring with structured Technology/ResearchArea Jaccard overlap.
- Weights moved to `application.yml` (`unisubmit.recommendation.weight.*`), score
  normalised by weight total (`RecommendationWeights.totalWeight()`).
- Per-signal breakdown + matched tech/area lists persisted on SubmissionSimilarity
  (migration `V16__recommendation_breakdown.sql`).
- Lecturer matching: `LecturerRecommendationService` + `dto/LecturerMatch` — aggregates
  Feedback history (`FeedbackRepository.findAllWithReviewedSubmissions()`), scores
  shared tags (research areas weighted 1.5×), review-volume tie-breaker, same-department
  bonus, top 3.
- Access control: `SubmissionAccessService.canDiscoverSubmission()` applied in
  precompute (candidate pool) AND at read time (viewer-aware), plus a discovery check
  added to `ProjectController.viewProjectDetail` (drafts no longer leak to peers).
- UI: expandable "Why this match" per-signal bar breakdown + match-reason line in
  `similarProjectsPanel`; "Suggested reviewers" widget on student/submission-detail.html
  via `lecturerMatchesPanel` fragment.
- Bug fixes shipped alongside: student dashboard crashed whenever a unit had a deadline
  (`#temporals.between` doesn't exist in Thymeleaf — replaced with
  `Unit.getDaysToDeadline()` transient); lecturer announcements badge class typo;
  "Deadline: null" in notification text; empty-state for lecturers with no teaching
  units; student assignments count now counts only open assignments.
- Theme refresh: Fraunces display font, brass/gold accent tokens, collegiate logo mark +
  favicon, page-head brass rule, refined tokens (all in base.css; fonts linked in
  layout.html and admin/layout.html).

**Remaining to finish Phase 5:**

1. **Browser-verify the new UI** (never visually checked). Run the app with the local
   profile, log in as S001, open a submission with computed similarities: check the
   "Why this match" expand/collapse, signal bars at sensible widths, lecturer widget,
   chips (gold = research areas, green = technologies), dark-theme contrast, and mobile
   at 375px. Fix whatever looks wrong in base.css / components.html.
2. **Lecturer-side transparency**: lecturer/review-split.html shows no recommendation
   info. Add the same similar-work + breakdown panel (read-only) so lecturers can spot
   overlapping submissions during review — reuse `similarProjectsPanel`.
3. **Precompute freshness**: recommendations only recompute when AI analysis runs.
   Tag edits by lecturers (`LecturerController.updateTags`) change the structured
   overlap but do NOT trigger `recommendationService.precomputeForSubmission()` — wire
   that call into `KnowledgeTagService.updateSubmissionTags`.

### Starter prompt (Phase 5 remainder)
```
[Paste Current State Snapshot first.]
Finish Phase 5 of UniSubmit. Three tasks:
(1) Wire recommendationService.precomputeForSubmission() into
KnowledgeTagService.updateSubmissionTags so lecturer tag edits refresh similarity scores.
(2) Add the existing similarProjectsPanel fragment (read-only, no collaborate buttons —
add a boolean fragment param) to lecturer/review-split.html so lecturers see overlapping
work with the "why this match" breakdown; pass similar submissions from
LecturerController.viewSubmission using recommendationService.findSimilarSubmissions(
submission, lecturerUser).
(3) Run the app with the local profile and visually verify the Phase 5 UI on
student/submission-detail (breakdown bars, suggested reviewers widget, chips, mobile
375px), fixing any CSS/template issues found. Compile must pass.
```

---

## Phase 5.5 — Fix & Refinement Backlog (from code review, 2 Jul 2026)

Ordered by severity. Each item is independent; do them in one sitting or cherry-pick.

### A. Security / correctness (do first)

1. **Insight API has no access control** — `AIInsightApiController`
   (`/api/ai-insights/{id}` GET and `/retry` POST) lets ANY authenticated user read any
   submission's summary/keywords/error and re-trigger analysis by iterating IDs.
   Fix: inject `SubmissionAccessService`, load the insight's submission, require
   `canDiscoverSubmission(currentUser, submission)` for GET and
   `canAccessSubmissionFile` for retry; return 404 (not 403) on failure to avoid ID
   probing. Current user comes from `@AuthenticationPrincipal CustomUserDetails`.

2. **LLM-failure fallback fabricates fake data** — in `AIInsightProcessingService`
   (~line 306), when the LLM call fails it invents technologies ("Spring Boot", "Java",
   "H2 Database"), canned objectives and a canned problem statement. This pollutes the
   knowledge model and recommendation signals with fiction for every failed analysis.
   Fix: fallback should keep only the real TF-derived keywords + extractive summary,
   leave technologies/researchAreas/objectives/problemStatement EMPTY, and prefix the
   summary with "(Automated fallback — AI analysis unavailable.)".

3. **Announcement deadlines clobber each other** — `AnnouncementService` writes
   `unit.submissionDeadline` on every ASSIGNMENT creation and NULLS it when any
   assignment announcement is deleted. With two assignments on one unit, creating the
   second overwrites the first's deadline, and deleting either wipes the unit deadline
   entirely. Fix: on create AND delete, recompute `unit.submissionDeadline` as the
   nearest future deadline among the unit's remaining ASSIGNMENT announcements
   (query AnnouncementRepository), instead of blind set/null.

4. **Notification "View" link wrong for non-owners** — notifications.html hardcodes
   `/student/submission/{id}`; lecturers or group members without ownership get an
   access error. Fix: link to `/projects/{id}` (the role-agnostic project page).

5. **Login throttling missing** — no brute-force protection on `/login`. Add a simple
   in-memory attempt counter (ConcurrentHashMap keyed by username+IP, lock 15 min after
   5 failures) via an AuthenticationFailureListener; document it in SecurityConfig.

### B. Robustness / performance

6. **AI pipeline transaction shape** — `performAnalysisAsync` is `@Transactional` and
   holds that outer transaction (and DB connection) open while blocking up to 30s on an
   inner `CompletableFuture` that runs its own `TransactionTemplate`. On failure the
   outer entity save can race the inner committed state. Refactor: mark PROCESSING in
   a short standalone tx, run the pipeline work (which already has its own tx), then
   mark COMPLETED/FAILED in another short tx; drop `@Transactional` from the outer
   method. Also make the 30s timeout configurable
   (`unisubmit.ai.timeout-seconds`, default 120 — GROBID + LLM regularly exceeds 30s).

7. **N+1 queries in precompute** — `RecommendationService.precomputeForSubmission`
   lazily loads aiInsight/technologies/researchAreas per candidate (~50 candidates ×3
   queries). Add a fetch-join query to SubmissionRepository for the candidate pool
   (JOIN FETCH aiInsight LEFT JOIN FETCH technologies LEFT JOIN FETCH researchAreas)
   and use it for both pool queries.

8. **Header does heavy work on every request** — `GlobalModelAttributes.
   pendingCollaborationCount` builds the entire inbox view per page load. Add
   `long countByRecipientAndStatus(User, CollaborationRequestStatus)` to
   CollaborationRequestRepository and use it.

9. **Shared-tag display casing mangled** — `RecommendationService.titleCase()` turns
   "PostgreSQL" into "Postgresql" in the matched-tag chips. Keep a lowercase→original
   map when building the tag sets and emit original casing instead of re-title-casing.

10. **File validation nits** — FileStorageService: error message mentions ".zip" but
    zip is not allowed (fix message); multipart limit in application.yml is 50MB while
    the service rejects >25MB (lower multipart to ~26MB so oversize uploads fail fast
    with a clear error).

### C. UX / product refinements

11. **Trim the manual tag editor** (owner decision made: yes, trim). In
    lecturer/review-split.html reduce the six multi-selects to TWO editable categories —
    Technologies and Research Areas (the only ones the recommendation engine uses).
    Frameworks/Databases/Languages/Skills stay in the model, LLM-populated, shown
    read-only. Simplify admin/tags.html to emphasise the same two categories (keep the
    other tables reachable but collapsed). Keep
    `KnowledgeTagService.updateSubmissionTags` backward-compatible (null lists = no
    change to that category).

12. **Unit tests for the scoring math** — none exist for RecommendationService or
    LecturerRecommendationService, yet they're pure functions (great viva material).
    Add plain JUnit tests (no Spring context): Jaccard edge cases, weight normalisation,
    viewer filtering with a stubbed access service, lecturer ranking order.

13. **Empty/loading polish** — pass through main pages checking empty states and
    the reduced-motion media query still hold after the theme refresh.

### Starter prompt (Phase 5.5)
```
[Paste Current State Snapshot first.]
Work through the Phase 5.5 backlog in UniSubmit-Feasible-Roadmap.md, section by section
(A security items first, then B, then C). Each item names the file, the problem, and
the intended fix — implement exactly those. After each section run
`mvnw.cmd -q compile -DskipTests`. For item 12 write pure JUnit tests without a Spring
context. Do not refactor beyond what an item asks.
```

---

## Phase 6 — Explainable Academic Assistant — ✅ DONE (endpoints only; UI removed at owner's request)

**Goal:** the one remaining LLM touchpoint — turn already-computed recommendation/DNA
data into natural language. The LLM explains; it never computes the match itself.

### Features
- One endpoint: given a submission + its precomputed SubmissionSimilarity breakdowns +
  LecturerMatch results + AIInsight fields, ask the LLM (reuse the existing
  OpenAI-compatible client pattern from AIInsightProcessingService — do NOT add
  spring-ai) to write a short explanation paragraph.
- Strict contract: the LLM receives only the already-computed structured data as
  compact JSON — never raw DB access, never other students' full documents.
- A small Q&A box scoped ONLY to the current submission's own extracted text + insight
  fields.

### Security
- Enforce `SubmissionAccessService` checks before the endpoint runs.
- Prompt-injection guardrail: system prompt must state that document text is untrusted
  DATA, not instructions (a malicious PDF could embed instructions aimed at the AI).
- Rate-limit: max ~10 assistant calls per submission per hour (in-memory map is fine).

### UI
- Explanation rendered as a visually distinct quote/callout block (use the gold accent
  border — `--gold` token) BELOW the computed breakdown, labelled "AI interpretation of
  the scores above".
- Simple chat-style input for the scoped Q&A with an "answers come only from this
  document" note.

### Starter prompt (Phase 6)
```
[Paste Current State Snapshot first.]
Implement Phase 6: Explainable Academic Assistant. Add an AssistantService that builds
a compact JSON context from a submission's persisted SubmissionSimilarity breakdown
rows, LecturerRecommendationService results, and AIInsight fields, then calls the same
OpenAI-compatible HTTP endpoint AIInsightProcessingService already uses (reuse its
api-key/base-url/model config) with a system prompt that (a) restricts the model to the
provided data only and (b) declares extracted document text as untrusted data, never
instructions. Endpoints: POST /api/assistant/{submissionId}/explain and
POST /api/assistant/{submissionId}/ask {question} — both guarded by
SubmissionAccessService.canDiscoverSubmission (404 on failure) and a simple in-memory
rate limit (10/submission/hour). Degrade gracefully to a friendly message when no API
key is configured. UI: gold-bordered callout block + small Q&A input on
student/submission-detail.html below the similar-work card, loaded via fetch with the
CSRF header pattern from static/js/app.js. Compile must pass.
```

---

## Phase 7 — Measured Intelligence — ✅ DONE (see status header for delivery notes)

Do these AFTER 5.5/6; each is independent. Priority order:

1. **Evaluation harness (do this one even if nothing else).** Use logged ground truth —
   accepted CollaborationRequests and lecturer tag corrections — to measure the
   recommender: precision@5 / MRR under different `unisubmit.recommendation.weight.*`
   configs. Implement as an admin-only page or a CommandLineRunner report that prints a
   table. This converts "I built features" into "I built and validated a system" for
   the report/viva, and is exactly why the weights were made configurable.
2. **Hybrid semantic search page.** One search bar: embed the query via the SPECTER
   sidecar → pgvector KNN (`<=>` cosine operator, native query), in parallel Postgres
   full-text (`tsvector` on title+summary), fuse with Reciprocal Rank Fusion
   (score = Σ 1/(60+rank)). Results respect canDiscoverSubmission. Postgres-only
   feature — degrade to keyword-only on H2.
3. **Near-duplicate / integrity check.** At upload time, compare the new embedding
   against the whole corpus (pgvector KNN, threshold ~0.95 cosine); surface a private
   "very similar prior work exists" banner to the owning student and to lecturers on
   the review page. Not an accusation — a signal.
4. **Research landscape map.** Cluster all submission embeddings (k-means, k≈8, plain
   Java or the sidecar) + 2D projection, rendered as an SVG dot map on an admin
   analytics page ("the university's research landscape").
5. **BM25 keyword upgrade + retrieve-then-rerank** (optional): replace raw keyword
   intersection with BM25 scoring; optionally add a cross-encoder rerank endpoint
   (ms-marco-MiniLM) to the Flask sidecar for the top-20 similar list.
6. **OCR fallback** for scanned PDFs (Tika currently yields empty text → pipeline
   fails): add Tesseract via tess4j or a sidecar endpoint, triggered when extraction
   returns < ~200 chars.
7. **Blind review mode** (no ML): hide student identity from lecturers until a grade is
   submitted — genuine fairness feature, small change to review-split.html + a config
   flag.

---

## Phase 8 — AI-Powered Collaboration Discovery — 📋 NEXT UP

> **Goal**: Replace the "same unit = match" noise with a genuine cross-disciplinary
> collaboration engine that uses AI to surface high-value partnerships — upperclassmen
> who can mentor, students from other departments working on the same real-world problem,
> and complementary-skill pairings where one student's limitation is another's strength.

### The core insight

The current similarity engine conflates **integrity detection** ("is someone copying?")
with **collaboration discovery** ("who could I learn from?"). These have opposite
signals: same-unit is a red flag for integrity but worthless for collaboration. A
student in Computer Science building a traffic-prediction ML model and a Civil
Engineering student optimising traffic-light timings share zero keywords and zero units,
but they are a *perfect* collaboration pair. Phase 8 splits these concerns and adds an
LLM reasoning layer that no amount of keyword matching can replicate.

### Architecture: two-stage pipeline

**Stage 1 — Mechanical pre-filter (fast, cheap).** A collaboration-specific scoring
path in `RecommendationService` that:
- Sets the unit weight to **zero** (same class ≠ interesting).
- Pulls candidates from the **entire submission corpus**, not just same-unit + recent.
- Boosts semantic (SPECTER embedding) + technology + research-area + problem-domain
  signals.
- Explicitly **deprioritises or excludes same-unit matches** (that's your classmate
  doing the same assignment).
- Favours **completed/archived submissions** (mentorship value) and
  **cross-department** pairs (interdisciplinary value).
- Outputs the **top ~15 candidates** per submission.

**Stage 2 — LLM collaboration assessment (smart, targeted).** ONE batched call per
submission to the configured OpenAI-compatible endpoint (same api-key/base-url/model
config as the AI pipeline — provider-agnostic, not hardwired to any vendor): send all
~15 shortlisted candidates' insight contexts in a single prompt and get a JSON array
back — 1 call instead of 15, ~90% cheaper. Each element is a structured assessment:

```
{
  "collaboration_value": "HIGH | MEDIUM | LOW | NONE",
  "collaboration_type": "mentorship | skill_exchange | interdisciplinary | scale_up | data_sharing",
  "what_a_gains": "What Project A's student gains from this collaboration (1 sentence)",
  "what_b_gains": "What Project B's student gains (1 sentence)",
  "pitch": "A 2-sentence natural language explanation of why these two should connect",
  "complementary_gaps": "Specific limitations in one project that the other addresses"
}
```

This is persisted alongside the similarity record so it's computed once (async, like the
existing AI pipeline) and displayed instantly on subsequent page loads. Only HIGH and
MEDIUM results are shown to users.

**Amendments locked in after design review (build these, they are not optional):**
- **Keyless degradation**: no OPENAI_API_KEY (the local machine's default state) must
  NOT mean an empty page. Persist Stage 1's shortlist as CollaborationMatch rows with
  collaborationValue = UNASSESSED and show them honestly labelled "mechanical match —
  AI assessment pending"; upgrade rows in place when a key appears.
- **Assessment caching**: skip re-assessing a pair when neither side's latest
  contentHash nor insight has changed since computedAt — re-uploads must not re-bill
  unchanged pairs.
- **Pair canonicalisation**: store one row per unordered pair (lower submission id =
  A); both students see the same row, rendered viewer-relative ("what YOU gain"
  first). Precompute from either side must not create a duplicate.
- **Exclusions**: same student's other submissions, same project-group members, and
  same-unit candidates never enter the shortlist.
- **Privacy gates Stage 1**: discoverableForCollaboration=false means the student's
  data is never sent to the LLM at all — not merely hidden from results.
- **Grounding guardrail**: the LLM sees only already-extracted insight fields (never
  raw documents), the system prompt declares that text untrusted data, and if it
  cannot name a CONCRETE gain for both sides it must return NONE — an empty page
  beats hallucinated flattery. Pitches carry a small "AI-generated interpretation"
  label (same convention as Phase 6).
- **Prefilled connect message**: the pitch pre-populates the connect request so the
  recipient understands why a stranger from another department is reaching out —
  this should directly lift the acceptance-rate metric.

### What the LLM enables that scoring alone cannot

1. **Complementary gap detection.** The LLM reads both project descriptions and notices
   that Project A says "our limitation is the lack of real-world sensor data" while
   Project B collected 6 months of IoT sensor data. No keyword overlap, no tag match —
   only an LLM reading the actual text connects those dots.

2. **Collaboration type classification.** Each match is tagged as one of:
   - **Mentorship** — an upperclassman completed a similar project and can guide a
     junior starting one.
   - **Skill exchange** — one has ML expertise, the other has domain knowledge and
     field data.
   - **Interdisciplinary** — different departments, different tools, same real-world
     problem (CS + EE on IoT, Architecture + Civil on structural analysis).
   - **Scale-up** — one built a prototype, the other has access to a real deployment
     environment.
   - **Data sharing** — one collected a dataset the other needs.

3. **Natural language pitch.** Instead of "12% Possible match — Same structural domain
   (Unit)", the student sees:
   > 🤝 **High-value collaboration** — *Interdisciplinary match, Electrical Engineering*
   >
   > Jane (3rd year) built an IoT sensor network for campus environmental monitoring
   > but needs help with the data analytics pipeline. Your ML-based anomaly detection
   > project could provide exactly that, while her deployed sensor network gives you
   > access to real-world time-series data you currently lack.

4. **Noise rejection.** The LLM filters out pairs that look similar mechanically but
   aren't useful: two students doing the exact same assignment (not collaboration,
   that's the syllabus), projects that share keywords but differ completely in scope, or
   early-stage work too immature to offer anything.

### Tasks

1. **Add `problemDomain` to the LLM extraction prompt.** One line change in the
   existing AI pipeline prompt template — ask Gemini to also extract 1–3 broad
   application domains (healthcare, transportation, agriculture, energy, education,
   manufacturing, finance, environment, security). Store as a new `@ElementCollection`
   on `AIInsight`. These are deliberately broad and cross-disciplinary.

2. **New entity: `CollaborationMatch`.** Separate from `SubmissionSimilarity` (which
   stays for integrity). Fields: submissionA, submissionB, mechanicalScore,
   collaborationValue (enum HIGH/MEDIUM/LOW/NONE), collaborationType (enum),
   whatAGains, whatBGains, pitch, complementaryGaps, computedAt. Flyway migration V18.

3. **Collaboration scoring path in `RecommendationService`.** A new method
   `precomputeCollaborationMatches(Submission)` that:
   - Builds a candidate pool from ALL submissions (not just same-unit).
   - Scores with unit weight = 0, high semantic/tech/area/domain weights.
   - Excludes same-unit candidates.
   - Returns top 15 by mechanical score.

4. **LLM collaboration assessment service.** A new `CollaborationAssessmentService`
   that takes the top 15 candidates and makes ONE batched call for all pairs (JSON
   array in, JSON array out). Uses the same OpenAI-compatible endpoint config as
   `AIInsightProcessingService`. Runs async after AI analysis completes. Persists to
   `CollaborationMatch`: HIGH/MEDIUM kept and shown, LOW/NONE kept but hidden (they
   suppress re-assessment), UNASSESSED tier when keyless. Honours the caching and
   grounding amendments above.

5. **Opt-in visibility.** Add a boolean `discoverableForCollaboration` to
   `StudentProfile` (default true). Students can toggle this in their profile settings.
   The collaboration scoring path respects this flag.

6. **Contact request flow — REUSE `CollaborationRequest`, do not build a parallel
   mechanism.** The existing request/accept/decline flow, inbox UI, and notifications
   already work, and accepted requests are the evaluation harness's ground truth — a
   second flow would split that signal. "Request to Connect" creates a
   CollaborationRequest with the LLM pitch prefilled as the message. The only new
   behaviour: acceptance reveals the counterpart's contact email on the match card.

7. **"Discover Collaborators" page.** A new student-facing page (`/collaborations` or
   `/discover`) separate from the similarity panel on submission-detail. Shows
   AI-enriched matches grouped by collaboration type, with the LLM pitch text,
   type badges, and the "Request to Connect" button. Filters by department and
   collaboration type.

8. **Admin analytics.** Extend the evaluation harness to track collaboration match
   acceptance rate (how often students click "Request to Connect" and get accepted).
   This is the ground truth for tuning the collaboration weights, just as accepted
   `CollaborationRequest` records are ground truth for the similarity engine.

9. **Cross-department demo seed data.** The feature is invisible on a corpus of seven
   same-unit submissions (same-unit is excluded by design!). Ship a seeder (local
   profile only) with ~12 submissions across 3–4 departments/programmes with distinct
   but overlapping topics and varied student years, so mentorship and
   interdisciplinary matches actually appear during the owner's manual test and demo.

### Starter prompt

```
[Paste the Current State Snapshot first — it contains the toolchain setup, the owner's
working agreement, and the rules. Then:]

Implement Phase 8 — AI-Powered Collaboration Discovery — from
UniSubmit-Feasible-Roadmap.md, including ALL the "Amendments locked in after design
review". Summary: the similarity engine conflates integrity detection with
collaboration discovery. Split them. Stage 1: precomputeCollaborationMatches() in
RecommendationService — candidate pool from the WHOLE corpus, unit weight zero,
same-unit/same-student/same-group EXCLUDED, semantic/tech/area/problemDomain boosted,
top ~15. Stage 2: CollaborationAssessmentService — ONE batched call to the configured
OpenAI-compatible endpoint per submission returning a JSON array of
{collaboration_value, collaboration_type, what_a_gains, what_b_gains, pitch,
complementary_gaps}; grounding guardrail (insight fields only, untrusted-data clause,
NONE over invention). Persist in CollaborationMatch (V18 migration; canonical pair
ordering, UNASSESSED tier when keyless, hash-based re-assessment skip). Also: add
problemDomain to the AIInsight extraction prompt + @ElementCollection;
discoverableForCollaboration opt-out on StudentProfile gating Stage 1; /discover page
(dark, minimal — matches grouped by type, viewer-relative "what you gain", AI label
on pitches, Request to Connect = existing CollaborationRequest with pitch prefilled,
contact email revealed on accept); acceptance-rate row on /admin/evaluation;
cross-department seed data (local profile) so the page is demonstrable.

Finish: mvnw.cmd compile exit 0, unit tests green (add tests for the Stage 1
exclusions and the batched-response parsing), app running on :8080, then hand the
owner a manual test list. Do NOT browser-test on their behalf.
```

---

## Phase 9 — Hardening & Completion (gap fixes from the 3 Jul review) — ✅ 6/7 DONE (4 Jul)

> Done: (1) live smoke check of every major page per role (200 + full render — automated
> MockMvc suite still worth adding), (2) deadline reminder scheduler (@EnableScheduling,
> hourly, 3-day/1-day windows, dedup by message), (3) version change notes (re-upload
> field → SubmissionVersion.changesSummary → timeline), (4) admin password reset (modal +
> BCrypt), (5) final-grade CSV export per unit (blind-mode aware). Also fixed: lecturer/
> student accounts now ALWAYS get a profile (auto-gen id when blank) + a startup backfill,
> so admin edit-save works. DEFERRED: (6) audit coverage for non-submission events —
> needs AuditLog.submission made nullable (V20) + new AuditAction values; skipped to avoid
> a half-done schema change. (7) README rewritten. 43 unit tests green.

> **Goal**: close the engineering and product gaps found after Phase 7 shipped. Items
> are independent — do them in the order listed (highest value-for-effort first).
> None of them add new visible surface area beyond what is listed; remember the
> owner's minimal-UI rule.

### Tasks

1. **Page-rendering smoke tests (do this first).** The worst historical bug was a
   Thymeleaf template referencing a property that didn't exist
   (`sim.submission.student.studentId`) — the page silently truncated mid-render and
   was only caught by the owner clicking around. Add MockMvc tests that seed a small
   H2 dataset (student with submission+insight+similarity, lecturer with assignment,
   admin), log in as each role, and GET every major page (student dashboard,
   submission-detail, announcements, groups, inbox, search, lecturer dashboard,
   review-split, notifications, admin dashboard/accounts/evaluation/landscape)
   asserting 200 + a marker string near the END of each template — that end-marker is
   what catches mid-render truncation. This test class is the safety net every later
   phase runs against.

2. **Deadline reminder scheduler.** NotificationType.DEADLINE and the ⏰ icon exist
   but NOTHING creates deadline notifications — the feature is half-built. Add a
   @Scheduled job (@EnableScheduling; run hourly) that notifies enrolled/programme
   students of ASSIGNMENT announcements due within 3 days and again within 1 day.
   Persist sent-marker state (simplest: a small reminder log table, V19, or a
   deterministic "already notified?" check against AppNotification content) so
   reminders never duplicate. Reuse getAnnouncementsForStudent's unit-resolution
   logic (enrollments + programme + submitted-to units).

3. **Version change notes.** SubmissionVersion.changesSummary exists in the schema
   and is rendered nowhere. Add an optional "What changed in this version?" text
   input to the student re-upload form, save it, show it in feedbackTimeline under
   the version row. No migration needed (column exists). Lecturers get a changelog
   for free.

4. **Admin password reset.** The login page says "Forgot password? Contact your
   administrator" but the admin console has no reset action — a forgotten password
   is a dead end. Add a "Reset password" button to the accounts table (modal: new
   password, confirm; BCrypt-encode; audit-log it). No email infrastructure yet, so
   the admin communicates the new password out-of-band.

5. **Final grade + CSV export.** Grades live only on per-version Feedback. Add a
   lecturer "Export marks (CSV)" per unit: one row per submission — student
   admission number, name (respect blind-mode: omit if ungraded), title, status,
   latest grade, graded-by, date. Content-Disposition attachment; no schema change
   (derive latest grade from feedback).

6. **Audit coverage for admin/security events.** AuditService only logs submission
   lifecycle. Also record: account suspension/unsuspension (who, why), account
   deletion, announcement create/delete, password reset (event, never the value),
   login lockouts (from LoginAttemptService). Where an event has no submission,
   allow a null submission on AuditLog (nullable column change → V20 if the column
   is currently NOT NULL; check first).

7. **README + report alignment.** README.md predates the redesign and Phases 5.5–9.
   Rewrite: what the platform does now, architecture sketch (pipeline → engine →
   evaluation), the intelligence story (six signals, adaptive normalisation,
   identical-document integrity, evaluation harness numbers), toolchain/run
   instructions from the snapshot, config-flag table (grobid/specter/ocr/search
   semantic/blind-mode/refresh-on-startup), honest scale ceilings (in-memory
   search/landscape/evaluation — fine for hundreds of submissions; in-memory
   throttling — single node).

### Operational notes for the owner (no code)

- The single biggest quality jump needs zero code: set OPENAI_API_KEY (real analysis
  instead of fallback) and run the SPECTER sidecar (semantic signal + landscape
  embeddings + semantic search all light up).
- Move the project out of the OneDrive-synced folder (or exclude unisubmit-db* and
  uploads-local from sync) — cloud sync file-locking is a classic cause of H2
  corruption.

### Starter prompt

```
[Paste the Current State Snapshot first — toolchain, owner's working agreement, rules.]

Work through Phase 9 — Hardening & Completion — in UniSubmit-Feasible-Roadmap.md, in
task order (1→7). Each task names the file/feature, the gap, and the intended fix —
implement exactly those; no extra UI surface. Task 1's smoke tests are the safety net:
write them first and keep them green through every later task. Schema changes ship
V19+/V20+ Flyway migrations AND must work on the H2 ddl-auto local profile.

Finish: mvnw.cmd compile exit 0, ALL tests green, app running on :8080, and a short
manual test list for the owner (they do all browser testing themselves).
```

---

## Cross-cutting hygiene (unchanged, still applies)

- **CSRF** stays on; never disable for convenience.
- **File access**: FileController's `canAccessSubmissionFile` check is the template for
  any new file-serving endpoint.
- **Secrets**: `.env` is gitignored; never commit real keys; set a spend cap on the LLM
  provider console.
- **Schema changes**: every change ships a Flyway `V17+` migration for Postgres AND is
  compatible with the H2 `local` profile's ddl-auto path.
- **Empty states, inline form validation, mobile check on review-split.html** — verify
  whenever touching those templates.

## Deliberately excluded

Multi-university support, knowledge-graph/vector infra beyond pgvector, cross-institution
citation tracking, autonomous agents, policy simulation, plugin marketplace. Mention as a
single "Future Directions" paragraph in the final report — not build tasks.
