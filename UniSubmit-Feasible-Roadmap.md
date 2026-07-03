# UniSubmit — Feasible Roadmap (Solo-Buildable Edition)

> Status as of **3 July 2026**: Phases 1–6 complete, plus Phase 7 item 3 (near-duplicate
> integrity check via SHA-256 content hashes, V17 migration). Full UI redesign shipped
> ("Nocturne Laurel" dark theme). Phase 5.5 backlog done. Phase 6 assistant exists as
> guarded API endpoints only (/api/assistant/*) — its UI card was removed at the owner's
> request. Owner-driven changes beyond the roadmap: knowledge-tag UI removed everywhere
> (engine still uses LLM-populated tags internally; admin console at /admin/tags is
> unlinked but functional), adaptive score normalisation (unfireable signals excluded
> from the denominator; identical documents now read 100%), suspension actually blocks
> login + ejects live sessions, admin programme edit fixed, student announcements
> include units the student has submitted to, lecturer doc preview rebuilt, login
> brute-force throttling added. Remaining stretch: Phase 7 items 1–2, 4–7.

This roadmap keeps only what one student, working alone on the current Spring Boot
codebase, can realistically build and demo. Each phase is self-contained: goal, tasks,
and a ready-to-paste starter prompt — so any AI model or agent can pick up a phase in a
fresh chat with zero prior conversation context. **Always paste the "Current State
Snapshot" section below together with the phase's starter prompt.**

---

## Current State Snapshot (paste this with any starter prompt)

```
UniSubmit is a Spring Boot 4 / Java 17 / Thymeleaf / PostgreSQL academic submission
platform at C:\Users\BOM\Downloads\unisubmit-main\unisubmit-main (a git repo, branch main).

TECH: Spring Boot 4.0.5, Spring Security, Spring Data JPA, Thymeleaf 3.1, Lombok,
Flyway (V2–V16 in src/main/resources/db/migration), PostgreSQL + pgvector in prod,
H2 file DB via the `local` profile (Flyway OFF, ddl-auto: update — run with
`mvnw spring-boot:run -Dspring-boot.run.profiles=local`; seeds accounts
admin / L001 / S001, all password123). Compile check: `mvnw -q compile -DskipTests`
(on Windows use mvnw.cmd; the bash ./mvnw wrapper script is broken on this machine).

DOMAIN (src/main/java/com/unisubmit/domain): User (STUDENT/LECTURER/ADMIN) with
StudentProfile/LecturerProfile, hierarchy Faculty→Department→Programme→Unit→Curriculum,
AcademicYear/Semester, TeachingAssignment (lecturer↔curriculum), Enrollment,
Submission (student, curriculum, projectGroup, supervisors, status enum
DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/PROPOSAL/FINAL/ARCHIVED,
float[] embedding via VectorConverter, ManyToMany tag sets: technologies,
researchAreas, frameworks, databases, programmingLanguages, skills),
SubmissionVersion (+Feedback with optional grade), AIInsight (summary, keywords,
objectives, problemStatement, status PENDING/PROCESSING/COMPLETED/FAILED),
SubmissionSimilarity (score + per-signal breakdown columns + matched keyword/tech/area
element collections), SubmissionRelation (INSPIRED_BY/EXTENDS/RELATED), AuditLog
(append-only), Announcement (ANNOUNCEMENT/ASSIGNMENT + deadline), AppNotification,
Collaboration/CollaborationRequest, ProjectGroup, Reference (GROBID citations),
lookup entities Technology/ResearchArea/Framework/Database/ProgrammingLanguage/Skill.

AI PIPELINE (service/AIInsightProcessingService): Tika text extraction → optional
GROBID structured parsing (GROBID_ENABLED, http://localhost:8070) → LLM call to an
OpenAI-compatible endpoint (OPENAI_API_KEY / OPENAI_BASE_URL, default OpenRouter,
model default openai/gpt-4o-mini) returning strict JSON {summary, keywords, objectives,
technologies, researchAreas, problemStatement} → maps technologies/researchAreas onto
lookup tables (find-or-create, case-insensitive) → optional SPECTER2 embedding via a
Flask sidecar (specter-service/, SPECTER_ENABLED, http://localhost:5001, model
allenai/specter2_base) stored on Submission.embedding → triggers
RecommendationService.precomputeForSubmission().

RECOMMENDATION ENGINE (Phase 5, service/RecommendationService): blends SIX signals —
keyword overlap, title Jaccard, unit/department proximity, embedding cosine similarity,
structured Technology tag Jaccard, structured ResearchArea tag Jaccard — weights
configurable under unisubmit.recommendation.weight.* in application.yml
(config/RecommendationWeights.java), final score normalised by weight sum. Per-signal
breakdown persisted on SubmissionSimilarity (V16 migration). Read side
findSimilarSubmissions(submission, viewer) re-checks visibility per viewer.
service/LecturerRecommendationService recommends reviewers by aggregating each
lecturer's Feedback history tags vs the current submission (no LLM). Access rules in
service/SubmissionAccessService: canAccessSubmissionFile (strict) and
canDiscoverSubmission (discovery level — peers may see non-DRAFT work only).

UI: server-rendered Thymeleaf; design system in static/css/base.css ("Scholarly Dark":
ink charcoal surfaces, laurel green primary --primary/--brand, brass gold accent
--gold, Inter body + Fraunces display headings, mortarboard logo). Shared fragments in
templates/fragments/components.html: statusBadge, feedbackTimeline, aiInsightPanel,
similarProjectsPanel (with expandable per-signal "why this match" breakdown),
lecturerMatchesPanel. Key pages: student/dashboard, student/submission-detail (AI
insights + similar work + suggested reviewers + tags), student/announcements,
lecturer/dashboard, lecturer/review-split, lecturer/announcements, admin/* console,
notifications, login/register. Small dependency-free JS in static/js/app.js
(nav, AI status polling of /api/ai-insights/{id}, filters).

RULES FOR EVERY CHANGE: never break existing flows; new entity fields nullable or
defaulted; ship a Flyway migration (V17+) for every schema change AND remember the
local H2 profile applies schema via ddl-auto instead; match existing Lombok/JPA/
Thymeleaf patterns; finish by running `mvnw.cmd -q compile -DskipTests` (zero errors).
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

## Phase 5 — Recommendation Engine — 🔨 ~90% DONE

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

## Phase 6 — Explainable Academic Assistant

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

## Phase 7 — Measured Intelligence (stretch — strongest report material)

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
