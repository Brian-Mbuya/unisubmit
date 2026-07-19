# UniSubmit · CODEBASE-MAP (read this INSTEAD of re-reading the code)

> **Purpose:** one-file current-state map so an AI session (or a human) can work on this
> repo without re-reading 13.4k lines of Java + 2.5k of CSS. Verified against the code on
> 2026-07-17 (branch `v2.0-safety`). If you change the architecture, update this file.
> For design rules & load-bearing JS hooks read `FABLE5-HANDOFF.md` **§3 + §6** (still
> accurate). Roadmap/history: `ROADMAP.md`. **Stale in the handoff:** fonts are now
> self-hosted, error pages exist, base type is 15px, base.css is ~2540 lines — its §4
> audit items are mostly fixed.

## 1 · What it is
University project-submission platform. Student uploads a document → Tika extracts text →
AI/heuristic insight (summary, keywords, tags) → lecturer reviews/grades in a split view →
engine matches similar projects & surfaces collaborators. Admin manages the academic tree
(faculty→department→programme→unit→curriculum→teaching-assignment) + accounts + CSV/XLSX
bulk import. PWA (installable, offline shell, bottom nav) wrapped as an Android TWA/APK.

## 2 · Stack & run
- Spring Boot **4.0.5**, Java 17, server-rendered **Thymeleaf**, Spring Security, JPA/Hibernate.
- DB: **Supabase Postgres** (prod) / **H2 file** (local, `application-local.yml`, profile `local`).
- Frontend: ONE stylesheet `static/css/base.css` (design system "Nocturne Laurel", numbered
  sections, tokens at top) + ONE vanilla `static/js/app.js` (510 lines, IIFE, no deps) +
  `static/sw.js` (**VERSION `unisubmit-shell-v15`** — bump on any css/js/icon shape change!).
- Zero external runtime deps: fonts (Inter+Fraunces woff2), Chart.js vendored, icons local.
- Build: `JAVA_HOME=C:\Users\mbuya\.jdks\jdk-17.0.19+10` then `.\mvnw.cmd -B -ntp -DskipTests package`.
- Local run: `run-local.ps1 -Port 8090` → seeds `admin`/`L001`/`S001`, all `password123`.
- Deploy: push to `main` → auto-deploy (owner pushes, never the AI). Live URL in handoff §1.
- pom extras: tika 2.9.2, commons-csv, poi-ooxml (xlsx import), pgvector, lombok, flyway (disabled).

## 3 · Layout (where things live)
```
src/main/java/com/unisubmit/
  config/       seeders + runners + weight configs (see §7)
  controller/   thin MVC controllers (see route table §4); admin/* subpackage
  domain/       JPA entities (see §6)
  dto/          SimilarSubmission, CollaborationOpportunity, LecturerMatch,
                GroupSummaryDto, CollaborationInboxView, CollaboratorDTO(dead)
  exception/    GlobalExceptionHandler (catch-all → flash + redirect to Referer),
                SubmissionNotFound/Unauthorized/DuplicateEntity
  repository/   Spring Data; notable: SubmissionRepository.findWithRecommendationDataByIdIn
                (fetch-join, kills N+1), TeachingAssignmentRepository.findByCurriculumIdIn
  security/     SecurityConfig, CustomUserDetails(Service), LoginAttemptService (5 fails
                per user+IP → 15-min lock, in-memory), SuspensionEnforcementFilter
  service/      all business logic (see §5)
src/main/resources/
  application.yml        heavily commented — READ ITS COMMENTS, they are the ops truth
  application-local.yml  H2 + demo seeding ON
  db/migration/          V1–V19 — DORMANT (Flyway disabled; Hibernate ddl-auto=update owns
                         the schema; re-adopt recipe = baseline-version=19, see yml comment)
  templates/             39 files: layout.html (shell) · fragments/{navbar,alerts,components,
                         hierarchy-select} · student/* lecturer/* admin/* · explore/
                         notifications/groups(→student/groups.html)/about/login/register/
                         forgot-password · error/{404,500,error} · ORPHANS: search.html AND
                         discover.html (GET /discover only redirects to /explore?tab=discover;
                         no controller returns "discover")
  static/                css/base.css · js/app.js · js/vendor/chart.umd.min.js · fonts/ ·
                         icons/ · sw.js · manifest.webmanifest · offline.html · .well-known/
Root: FABLE5-HANDOFF.md ROADMAP.md ROADMAP-ARCHIVE.md CODEBASE-MAP.md(this) Dockerfile docker-compose.yml(local
      PG convenience) deploy/(Caddy+systemd+Railway notes) specter-service/(python sidecar:
      SPECTER2 embeddings + OCR, off by default) run-local.ps1 github-ready/(1 stale README)
      screenshots/(2.5MB binary — never read)
```

## 4 · Routes (controller → view)
**Auth** (`AuthController`): GET/POST `/login /register /forgot-password`, GET `/about`, `/` root
redirect by role. **Health**: `/health`.
**Student** (`StudentController`, ROLE_STUDENT, `/student/**`):
dashboard · announcements · `submission/new`(GET form, POST create) · `submission/{id}`(detail:
similar work + lecturer matches + collab request statuses) · `submission/{id}/version`(POST) ·
`submission/{id}/retry-analysis`(POST) · inbox · `collaboration-requests`(POST send,
`{id}/accept|decline`).
**Lecturer** (`LecturerController`, ROLE_LECTURER): dashboard(filters: status/type; grouped by
unit) · announcements(GET/POST, `{id}/delete`, `{id}/toggle-late-window`) · `submission/{id}`
(review-split; blind-review flag) · `submission/{id}/review`(POST feedback+status+grade) ·
`units/{unitId}/marks.csv`. (`submission/{id}/tags` EXCISED in Phase 3.)
**Admin** (`controller/admin/*`, ROLE_ADMIN): dashboard · accounts(create/edit/suspend/delete/
reset-pw) · faculties/departments/programmes/units/curricula/assignments(CRUD) · tags ·
evaluation(match-weight what-if harness → EvaluationService) · landscape(=AnalyticsService
PCA scatter of corpus) · import(CSV/XLSX students: page/preview/apply/results.csv/template).
**Shared authenticated**: `/explore`(ExploreController: tabs archive|search|matches; Discover
tab now STUDENT-only via sec:authorize) · `/discover`(cross-dept opportunities) ·
`/notifications`(+`/mark-read`, +`/open/{submissionId}` → role-aware redirect to the right
detail page) · `/groups`(ProjectGroupController: list/create/addMember/removeMember +
`/{id}/leave` (non-leader self-removal; leader blocked) — still no delete) ·
`/projects/{id}`(ProjectController → student/project-detail, group-scoped; role-aware Back +
viaCollaboration hint) · `/files/{path}`(FileController, access-checked
download; 404 msg explains storage-fix history).
**APIs**: `/api/ai-insights/{id}`(GET status poll) `/api/ai-insights/{id}/retry`(POST) ·
`/api/ai/analyze-draft-file`(POST multipart → 3 title suggestions; the ONLY live suggest path;
rate-limited DRAFT_TITLES bucket). (suggest-title/rename/assistant endpoints EXCISED in Phase
3.) · `/api/users/search`(member picker) ·
`/api/academic/*`(anon; register-form cascading selects).

## 5 · Services (one line each — read the file only if your task touches it)
- **AIInsightProcessingService** (829, the heart): async pipeline per upload — Tika text
  (→GROBID structured if enabled →OCR sidecar if scanned) → LLM via **`service/ai/LlmClient`**
  (the ONE HTTP LLM path — untrusted-data system prompt, lenient JSON, 1 bounded re-ask;
  callOpenAi/suggestTitlesForDraft delegate to it) OR local heuristic fallback (TF keywords +
  extractive summary; fabricates NO tags) → maps technologies/researchAreas (find-or-create)
  → SPECTER embedding if enabled → precomputes matches + collab shortlist → Stage-2 LLM
  collab assessment. Careful tx discipline (no long tx; TransactionTemplate per step;
  timeout-bounded future). Also `suggestTitlesForDraft`(live) / `suggestTitles`(dead).
- **RecommendationService** (420): 6-signal weighted match score (keyword/title/unit/semantic/
  technology/researchArea; weights in yml), adaptive normalisation (unavailable signals leave
  the denominator), same-unit alone NEVER creates a match, SHA-256-identical files force 100%
  "Identical document". Persists SubmissionSimilarity with per-signal breakdown for the UI bars.
- **CollaborationDiscoveryService** (432): Stage-1 whole-corpus cross-disciplinary shortlist
  (excludes same unit; mentor detection); **CollaborationAssessmentService** (361): Stage-2 LLM
  verdict/pitch on shortlisted pairs (no-op without key).
- **SubmissionService** (417): create/version (deadline guard now honours the lecturer's late
  window via injected AnnouncementService — past-deadline uploads allowed while open + flagged
  `version.late=true`; strict curriculum: a student WITH a programme must have a matching
  curriculum or gets a clear error, only profile-less accounts keep the first-curriculum
  fallback; approved guard; group perms; SHA-256 content hash) → triggers analysis; lecturer
  queue via TeachingAssignments; access checks; feedback+status+grade with notifications (owner
  + group members); batch lecturer populate (anti-N+1). Dead tail: addSupervisor/removeSupervisor.
- **SearchService** (245): BM25-ish keyword search + optional pgvector semantic channel (flag).
- **KnowledgeTagService** (379): master-list CRUD for 6 tag families; only Technology +
  ResearchArea are ever attached to submissions (LLM path). Framework/Database/Language/Skill
  = dead weight (see §9).
- **service/ai/LlmClient**: the single LLM HTTP client (completeJson + extractCapped +
  DEFAULT_SYSTEM_PROMPT); hasKey() gates every AI surface. **service/ai/AiRateLimitService**:
  sliding-window per-(user,bucket) limiter — buckets DRAFT_TITLES(10/h)/RERUN(4/h)/
  DRAFT_FEEDBACK(15/h), in-memory (resets on redeploy). (AssistantService was EXCISED in
  Phase 3 — its limiter/extractCapped/untrusted-prompt were salvaged into these two.)
- **AnalyticsService** (358): PCA (power iteration) over one-hot keyword+tag vectors → admin
  landscape scatter. **EvaluationService** (176): precision@K + collab-health metrics for the
  admin evaluation page.
- **CsvImportService** (302): students CSV/XLSX import — parse+validate (no writes) → session-
  stash → per-row apply (SecureRandom passwords) → one-time credentials CSV.
- **AnnouncementService** (300): per-unit notices/assignments (deadline sets unit deadline,
  late-window toggle); recipients = enrollments ∪ programme-mapped students.
- **UserService** (185): create/update accounts + profiles (bcrypt), suspend/restore, admin
  password reset. **AcademicHierarchyService/CourseService/UnitService**: tree lookups.
  **ProjectGroupService** (179): groups CRUD/membership. **CollaborationRequestService** (143):
  request lifecycle → Collaboration on accept. **LecturerRecommendationService** (137):
  supervisor suggestions by tag overlap with lecturer profiles.
  **SubmissionAccessService** (88): single source of discover-visibility truth; +isCollaborator
  OnlyFileAccess (drives the "unlocked as collaborator" hint). **AIInsightService**: retry/rerun
  now atomic via `AIInsightRepository.transition(id,to,from)` (@Modifying conditional UPDATE —
  performAnalysisAsync claims PENDING→PROCESSING, only one winner; rerunAnalysis from
  COMPLETED/FAILED). **ProjectGroupService**: +leaveGroup (non-leader self-removal).
  **FileStorageService** (100): UUID-named store under upload dir, MIME/extension whitelist,
  25MB cap. **NotificationService/AuditService**: bell + immutable per-submission audit trail.
  **GrobidService/SpecterService/OcrService**: HTTP sidecars, all flag-gated OFF.
  **SubmissionRelationService** (102): rate-limited student-declared project links.
- Removed entirely: **CollaboratorService**(dead, zero refs).

## 6 · Domain (entities; join tables implied)
User(role STUDENT|LECTURER|ADMIN, deleted flag, suspension fields) 1–1 StudentProfile
(admissionNumber, programme→Course, year/sem, academicStatus, discoverable flag, enrollments)
| 1–1 LecturerProfile(staffId, expertise tags). Academic tree: Faculty→Department→Course
(=programme)→? ; Unit(department, submissionDeadline, lateWindow…) ; Curriculum(unit+programme
+year/sem) ; TeachingAssignment(lecturerProfile+curriculum) ; Enrollment(student+unit) ;
AcademicYear/Semester(reference). Submission(student, curriculum, status DRAFT|PROPOSAL|
SUBMITTED|UNDER_REVIEW|CHANGES_REQUESTED|APPROVED|REJECTED|FINAL|ARCHIVED, projectGroup?,
embedding float[] via VectorConverter, technologies/researchAreas [+4 dead tag sets],
supervisors(dead)) 1–N SubmissionVersion(filePath, originalName, size, contentHash SHA-256,
uploadedBy, changesSummary) 1–N Feedback(lecturer, message, grade 0-100). AIInsight 1–1
Submission(status PENDING|PROCESSING|COMPLETED|FAILED, summary, keywords, objectives,
problemDomains, problemStatement, errorMessage). SubmissionSimilarity(A,B, score + per-signal
scores + matched lists + reason + sameUnit). CollaborationMatch(Stage-1 row + Stage-2 verdict/
pitch), CollaborationRequest(PENDING|ACCEPTED|DECLINED)→Collaboration. ProjectGroup(leader,
members). Announcement(type ANNOUNCEMENT|ASSIGNMENT, unit, deadline, lateWindowOpen).
AppNotification(type, message, submissionId, read). AuditLog(submission NOT NULL, action,
detail, actor). Reference(GROBID-extracted citation). SubmissionRelation(a,b,type,creator).
Tag families: Technology, ResearchArea (live) · Framework, Database, ProgrammingLanguage,
Skill (dead). Lookup seeding: UnisubmitApplication CommandLineRunner (count==0 guarded).

## 7 · Config truths (memorise these)
- **AI is opt-in**: `OPENAI_API_KEY=NO_KEY` default → heuristic fallback everywhere; GROBID/
  SPECTER/OCR/semantic-search each have `unisubmit.*.enabled=false` flags. UI shows honest
  no-AI states. LLM: OpenRouter, model `openai/gpt-4o-mini` default.
- **Schema = Hibernate `ddl-auto=update`**, Flyway DISABLED (crash-loop history; yml comment
  = the recipe to re-adopt). Do NOT enable Flyway casually.
- Sessions: in-memory (redeploy = logout). JDBC sessions BLOCKED on non-Serializable
  CustomUserDetails wrapping the JPA User — needs a record-principal refactor (ROADMAP O2.1).
- Static assets: 7d HTTP cache, safe because sw.js precaches {cache:'reload'} + revalidates
  {cache:'no-cache'}; **the SW VERSION bump is the real cache-buster**.
- Uploads: local dir (`APP_UPLOAD_DIR`, default `uploads/`) — **ephemeral on PaaS** unless a
  volume is mounted at /app/uploads (ROADMAP D7, owner action). Missing files 404 with a
  friendly message.
- Multipart cap 26MB (service checks 25MB first for the friendly error).
- Seeders on boot: base accounts+lookups (always, idempotent) · RichTestDataSeeder (**always
  incl. prod on a fresh DB** — marker-guarded only; source of "Prof. Ada Demo" data) ·
  CollaborationDemoSeeder (env-gated, default off in prod) · ProfileBackfillRunner (repairs
  missing profiles, always) · RecommendationRefreshRunner (local only) ·
  DeadlineReminderScheduler (real feature: notifies upcoming deadlines).
- `forward-headers-strategy: framework` (TLS terminates at proxy).
- Blind review: `BLIND_REVIEW=true` masks identity until first grade (UI + marks CSV).
- manifest.webmanifest ships a student-only "New submission" shortcut for ALL roles (JSON has
  no per-role gating and no comments) — accepted trade-off: a lecturer/admin who taps it lands
  on /student/submission/new and hits the normal role guard. Left as-is (ROADMAP 2.7c).

## 8 · Frontend contract (summary — full list handoff §6)
- Restyle in base.css; **never rename** hook classes/ids/data-attrs; CSRF meta + hidden inputs
  everywhere; `backdrop-filter` is BANNED (crashed low-RAM phones); reduced-motion guards.
- app.js functions (all wired in one DOMContentLoaded): markActiveNav, mobile nav, insight
  polling (bounded 40×3s), retry buttons, review action select, admin role-fields+confirm,
  table search, submission filters, AI tabs, draft-title suggestions (#file change →
  analyze-draft-file), loading-submit spinners, nav progress bar, SW register, mobile toasts,
  page titles, native share, dropzone, rail collapse, row links.
- base.css: tokens at top (§01) — palette work happens THERE. Section index in its header.
  Breakpoints: 1200/1080/860/640/520 (mobile shell ≤860).

## 9 · VERIFIED dead code (grep-confirmed zero callers; safe-to-delete tiers)
**Tier 1 — ✅ EXCISED in V3 Phase 3 (all of the following are GONE):**
SearchController · DiscoverController · CollaboratorService + CollaboratorDTO ·
AssistantService + AssistantApiController (limiter/extractCapped/prompt salvaged into
`service/ai/*`) · AIInsightApiController `suggest-title`/`rename` endpoints ·
AIInsightProcessingService.suggestTitles(Long) · SubmissionService.addSupervisor/
removeSupervisor + Submission.supervisors mapping (DB join table `submission_supervisors`
left in place — drop manually if wanted) · LecturerController.updateTags + its tagService
injection · templates search.html/discover.html · github-ready/ · ProjectGroupController
`students` model-attr + userService injection.
**Tier 2 — dead subsystem, retire deliberately (touches entity model + admin UI):**
- Tag families Framework/Database/ProgrammingLanguage/Skill: entities, repos, Submission sets,
  KnowledgeTagService methods, seed blocks in UnisubmitApplication, their sections of
  admin/tags.html. Nothing can ever ATTACH them to a submission (only path was the dead
  updateTags endpoint; the LLM maps only Technology+ResearchArea; RecommendationService scores
  only those two). ~400+ lines across files.
**NOT dead (leave alone):** db/migration/* (dormant by design) · docker-compose.yml (local
Postgres convenience) · deploy/ (real ops docs) · UserService "legacy" createUser overload
(used by UnisubmitApplication + import) · EvaluationService/AnalyticsService (admin pages) ·
all of app.js.

## 10 · Known weaknesses (current, honest)
1. Test coverage is thin but improving — 13 test files (ROADMAP V3 Phase 2 added the floor:
   SubmissionServiceGuards, SubmissionAccessService, AiPipelineIdempotency, SecurityMatrix +
   RecommendationService cases; 65 tests green). Controllers/templates still largely untested.
2. ✅ RESOLVED (V3 Phase 3): RichTestDataSeeder is now @ConditionalOnProperty
   `unisubmit.demo.seed-rich-test-data` (DEMO_SEED_RICH, default false in prod; true in
   application-local.yml) — no more demo rows on a fresh prod DB.
3. Default `password123` accounts seeded everywhere incl. prod (handoff flags it; forced
   password change is "Later" in ROADMAP). (being addressed by ROADMAP V3 — partially; the
   forced-reset/seeded-password piece is parked to a security phase)
4. OSIV is on (Spring default) + entities rendered directly in Thymeleaf → lazy loads during
   render hold a connection; pool max is 5 — fine at pilot scale, a ceiling under load.
5. In-memory sessions + in-memory login-attempt map: both reset on redeploy; fine single-node,
   blocks horizontal scaling.
6. ✅ RESOLVED (V3 Phase 3): the triplicated LLM HTTP boilerplate is gone — all LLM calls go
   through `service/ai/LlmClient` (grep `HttpClient` in src/main/java hits only LlmClient +
   OcrService sidecar).
7. ✅ RESOLVED (V3 Phase 3): CSV escaping unified in `util/CsvUtil.escape` (both
   LecturerController.csv() and CsvImportService delegate to it).
8. Uploads ephemeral until the volume is mounted (ROADMAP D7, owner action).
9. Flyway disabled: schema history is unversioned while ddl-auto owns it (deliberate; re-adopt
   recipe documented in application.yml).
