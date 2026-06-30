# UniSubmit — Feasible Roadmap (Solo-Buildable Edition)

This roadmap keeps only what one student, working alone on the current Spring Boot
codebase, can realistically build and demo. Each phase is self-contained: it has its
own goal, concrete features, UI/UX notes, security notes, and a ready-to-paste prompt
so you can start a fresh chat per phase without re-explaining context every time.

Paste the "Starter prompt" at the top of a new conversation along with the current
`unisubmit-main` zip, and the phase's content as the task brief.

---

## Phase 1 — Academic Foundation

**Goal:** Turn the current flat user/unit/submission model into a real university
hierarchy, without touching AI.

### Features
- `School`, `Department`, `Programme` entities above the existing `Unit`
- Academic Year / Semester entities, with units offered per semester
- Group projects: a `ProjectGroup` entity with a leader + members, instead of
  one student per submission
- Multiple supervisors per project (many-to-many `Submission` ↔ `User` with role LECTURER)
- Project lifecycle status: `PROPOSAL → UNDER_REVIEW → FINAL → ARCHIVED`
  (extends your existing `SubmissionStatus` enum)
- Basic in-app notifications table (new feedback, status change, deadline)

### UI/UX improvements
- Add a breadcrumb (School → Department → Programme → Unit) on submission detail pages
  so users always know where they are in the hierarchy
- Replace the flat "Units" dropdown in `new-submission.html` with a cascading
  School → Department → Unit selector to prevent wrong-unit submissions
- Add a notification bell icon in `layout.html` header with unread count badge
- Status pill colors on dashboards (grey=proposal, amber=review, green=final, slate=archived)
  for instant visual scanning

### Security improvements
- Add `@PreAuthorize` method-level checks on group membership changes (only the group
  leader or an admin should be able to add/remove members)
- Validate that a lecturer assigned as supervisor actually belongs to the unit's
  department before allowing assignment (currently nothing stops cross-department
  assignment)

### Starter prompt
```
I'm working on UniSubmit, a Spring Boot academic submission platform (attached zip).
Implement Phase 1 of my roadmap: Academic Foundation.

Add: School, Department, Programme entities above the existing Unit entity;
Academic Year and Semester entities; group project support (ProjectGroup with a
leader and members); multiple supervisors per submission; and a project lifecycle
status field. Use JPA entities consistent with the existing domain package style
(see Submission.java, Unit.java for conventions). Generate Flyway/SQL migration
files consistent with railway-collaboration.sql. Keep changes additive — don't
break existing Submission/Unit relationships.

After the backend changes, update new-submission.html to use a cascading
School -> Department -> Unit selector, and add a notification bell with unread
count to layout.html.
```

---

## Phase 2 — Knowledge Model

**Goal:** Design (and persist) the vocabulary the rest of the system will reuse —
no AI, just schema.

### Features
- New lookup entities: `Technology`, `Framework`, `Database`, `ProgrammingLanguage`,
  `ResearchArea`, `Skill`
- Many-to-many join tables: `Submission ↔ Technology`, `Submission ↔ ResearchArea`, etc.
- A `Reference`/citation entity for bibliography entries extracted later in Phase 3
- Seed data script for common technologies/research areas so the system isn't empty
  on first run

### UI/UX improvements
- Tag-style chips (not free-text) for technologies/research areas on the submission
  detail page — read-only chips for students, editable chips for the lecturer/admin
  who verifies them
- An admin screen to manage the lookup tables (add/merge duplicate technology names —
  e.g. "Spring" vs "Spring Boot" vs "SpringBoot")

### Security improvements
- Restrict lookup-table CRUD (`/admin/technologies`, etc.) to `ROLE_ADMIN` only;
  don't let lecturers silently create duplicate/junk tag values
- Add basic input sanitization on free-text tag creation to prevent stored XSS in
  tag names rendered back into Thymeleaf templates

### Starter prompt
```
Continuing UniSubmit (attached zip, Phase 1 already applied if done). Implement
Phase 2: Knowledge Model.

Add lookup entities: Technology, Framework, Database, ProgrammingLanguage,
ResearchArea, Skill, and a Reference entity for citations. Create many-to-many
join tables between Submission and each lookup entity. Write a SQL seed script
with ~30 common technologies and ~15 research areas relevant to a Kenyan
university CS/IT department.

Add an admin-only CRUD screen for managing these lookup tables (merge/rename
duplicates), restricted to ROLE_ADMIN via SecurityConfig. Update
submission-detail.html to show technologies/research areas as read-only chips
for students and editable chips for lecturers/admins.
```

---

## Phase 3 — Academic Intelligence Engine (LLM enters here, narrowly)

**Goal:** Replace the broken frequency-based "AI summary" with one real, scoped LLM
call that fills in the Knowledge Model from Phase 2.

### Features
- Add `spring-ai-starter-model-anthropic`, wire `ANTHROPIC_API_KEY`
- One structured-output prompt per submission: input = extracted text (Tika, already
  in place), output = JSON `{summary, keywords[], objectives[], technologies[],
  researchAreas[], problemStatement}`
- Map the structured JSON onto the Phase 2 lookup tables instead of free text
  (e.g. match "Spring Boot" string to existing `Technology` row, create if missing)
- Trim front-matter (first ~300–500 words: cover page, dedication, declaration) before
  sending text to the model — this is the actual fix for the Genesis/Pharaoh bug
- Keep the existing `AIInsightStatus` (PENDING/PROCESSING/COMPLETED/FAILED) flow as-is

### UI/UX improvements
- Show a loading skeleton (not just "PROCESSING" text) while the AI insight runs,
  since LLM calls take a few seconds longer than the old keyword-count method
- Add a "Re-analyze" button visible to admins only, for when extraction fails or a
  new file version is uploaded
- Surface the structured fields (objectives, problem statement) as labeled sections
  on the submission detail page instead of one undifferentiated summary blob

### Security improvements
- Never send the raw file path or any user PII in the LLM prompt — only the
  extracted document text
- Set a hard spend cap in the Anthropic console and log token usage per request so
  one large/malicious upload can't run up unexpected cost
- Validate/limit uploaded file size and MIME type before it ever reaches Tika or the
  LLM call (currently `FileStorageService` has no size or type restriction at all —
  this is a real gap today, not just an AI concern)

### Starter prompt
```
Continuing UniSubmit (attached zip). Implement Phase 3: Academic Intelligence Engine.

Replace the keyword-frequency logic in AIInsightProcessingService.java with a real
LLM call using spring-ai-starter-model-anthropic (model: claude-haiku-4-5). The
prompt should request strict JSON output: summary, keywords[], objectives[],
technologies[], researchAreas[], problemStatement. Trim the first 400 words of
extracted text before sending (skip cover pages/dedications). Map the returned
technologies/researchAreas arrays onto the Technology/ResearchArea lookup tables
from Phase 2 (match existing rows case-insensitively, create new ones if not found).

Also: add file size limit (e.g. 25MB) and MIME-type allowlist (pdf, docx) validation
in FileStorageService.storeFile before any Tika/LLM processing happens — this
doesn't currently exist and is a real gap. Update submission-detail.html to show
a loading skeleton during PROCESSING status and labeled sections for the
structured fields instead of one summary paragraph.
```

---

## Phase 4 — Academic Memory (lightweight version)

**Goal:** Store "Project DNA" as real structured data so later phases (analytics,
search, recommendations) can query it — without building a vector database or
knowledge graph engine.

### Features
- Persist the Phase 3 structured output permanently on `AIInsight` (already mostly
  there — extend with the new typed fields instead of just `summary`/`keywords`)
- A simple `SubmissionRelation` entity for manually or AI-suggested links
  ("inspired by", "extends") between two submissions — this gets you "research
  genealogy" in Phase 7-equivalent form without a graph database
- An audit/history table that logs status changes, feedback, and file uploads per
  submission (you likely have most of the raw data already via timestamps —
  this just centralizes it into one queryable timeline)

### UI/UX improvements
- A simple visual timeline component on the project detail page (vertical list:
  created → version 2 uploaded → feedback received → status changed) — this is a
  Thymeleaf + CSS component, no charting library needed
- A "Related projects" section using the `SubmissionRelation` table, separate from
  the keyword-based "Similar work" section, so manual curation and automatic
  similarity don't get confused in the UI

### Security improvements
- Audit log entries should be append-only at the application layer (no update/delete
  endpoint exposed, even to admins) so the history can't be quietly edited later
- Rate-limit the relation-creation endpoint to prevent spam-linking submissions

### Starter prompt
```
Continuing UniSubmit (attached zip). Implement Phase 4: Academic Memory (lightweight).

Extend AIInsight with typed fields for objectives, problemStatement (from Phase 3
JSON) if not already added. Create a SubmissionRelation entity (submissionA,
submissionB, relationType enum [INSPIRED_BY, EXTENDS, RELATED], createdBy, createdAt)
for manual/suggested project lineage links. Create an append-only AuditLog entity
that records status changes, feedback creation, and file uploads per submission
(no update or delete endpoint for this table, even for admins).

Add a vertical timeline UI component to project-detail.html driven by the AuditLog,
and a separate "Related projects" section driven by SubmissionRelation, distinct
from the existing keyword-similarity "Similar work" section.
```

---

## Phase 5 — Recommendation Engine (expand what already exists)

**Goal:** You already built the core of this (`RecommendationService`). This phase
is about making the existing scoring better and more transparent, not building
something new from scratch.

### Features
- Add Phase 2/3 signals into the existing weighted score: shared `Technology`/
  `ResearchArea` rows (now structured, not just free-text keyword overlap)
- Lecturer-matching: recommend lecturers whose past reviewed submissions share
  technologies/research areas with the current one (pure SQL aggregation —
  "no LLM, just math" as originally intended)
- Make weights configurable (a simple `application.yml` block) instead of hardcoded
  `0.5/0.3/0.2`, so you can tune and demonstrate different scoring strategies
  for your report/viva

### UI/UX improvements
- Show the score breakdown, not just the final label ("🔥 Strong Match") — e.g.
  a small expandable "why this match" row showing keyword overlap %, shared tech,
  same-unit flag
- Add a lecturer recommendation widget on the student's submission detail page

### Security improvements
- Ensure recommendation queries respect visibility rules (a student should never see
  similarity matches that expose another student's submission they don't have
  access to — check this against `SubmissionAccessService`, since `findByStudentNot`
  in `RecommendationService` currently has no access-control filtering applied
  before scoring)

### Starter prompt
```
Continuing UniSubmit (attached zip). Implement Phase 5: expand the existing
RecommendationService.

Add lecturer-matching: recommend lecturers based on technologies/research areas of
submissions they've previously reviewed (pure SQL/Java aggregation, no LLM). Move
the hardcoded scoring weights (0.5/0.3/0.2 in calculateSimilarity logic) into
application.yml as configurable properties. Incorporate the structured
Technology/ResearchArea overlap from Phase 2 into the existing keyword/title
scoring instead of just free-text keywords.

Important: audit RecommendationService's candidate pool queries
(findByUnitAndStudentNot, findByStudentNotOrderByCreatedAtDesc) against
SubmissionAccessService's visibility rules — currently no access check is applied
before a candidate is scored and shown, which could leak submissions a student
shouldn't see. Fix this.

Update submission-detail.html to show an expandable "why this match" breakdown
and a lecturer-recommendation widget.
```

---

## Phase 6 — Explainable Academic Assistant

**Goal:** The one remaining LLM touchpoint — turning the already-computed,
already-explainable recommendation/DNA data into natural language. The LLM writes
the explanation; it never computes the match itself.

### Features
- One endpoint: given a submission + its precomputed similarity/lecturer-match
  results (from Phase 5) + its Project DNA (from Phase 3), ask the LLM to write a
  short natural-language explanation paragraph
- Strict prompt contract: the LLM is only given the already-computed structured
  data, never raw access to search the whole database — this keeps it "explainable"
  rather than "hallucinating," matching your original design intent
- A basic Q&A box scoped only to the current submission's own extracted text +
  DNA (not the whole university's data) — a safe, small version of phase 6's
  "Academic Assistant" idea without needing real retrieval infrastructure

### UI/UX improvements
- Present the explanation as a quote-styled callout block, visually distinct from
  the raw computed data above it, so users understand "this part is the AI's
  interpretation, that part above is the system's actual calculation"
- A simple chat-style input box on the submission detail page for the scoped Q&A,
  with the answer always showing which submission fields it drew from

### Security improvements
- Enforce the same `SubmissionAccessService` check before this endpoint runs —
  a user must already be allowed to view the submission before asking the assistant
  about it
- Add prompt-injection guardrails: since the assistant reads extracted document
  text, treat that text as untrusted input in the system prompt (a malicious
  student could embed instructions in their PDF aimed at the reviewer's AI
  assistant) — explicitly instruct the model to treat document content as data,
  not as instructions

### Starter prompt
```
Continuing UniSubmit (attached zip). Implement Phase 6: Explainable Academic
Assistant.

Add an endpoint that takes a submission's already-computed RecommendationService
output and AIInsight DNA fields, and asks Claude (via the existing
spring-ai-starter-model-anthropic setup) to write a short natural-language
explanation of why the recommendations make sense. The LLM must only receive the
already-computed structured data as input — it should not query the database or
compute its own similarity. Also add a small Q&A feature scoped only to the
current submission's extracted text and DNA fields (not the whole database).

Security: enforce SubmissionAccessService checks before this endpoint runs.
In the system prompt, explicitly instruct the model to treat the extracted
document text as untrusted data, not as instructions, to guard against
prompt injection from a malicious uploaded document.

UI: render the explanation as a visually distinct callout/quote block on
submission-detail.html, separate from the raw computed scores above it. Add a
simple scoped chat input for the Q&A feature.
```

---

## Cross-cutting items (apply across all phases, not their own phase)

These aren't sized like a phase — they're ongoing hygiene worth doing alongside
whichever phase you're on.

### Security (general, not LLM-specific)
- **CSRF**: confirm Spring Security's default CSRF protection isn't accidentally
  disabled anywhere (not visible in current `SecurityConfig`, which is good —
  just don't disable it later for convenience)
- **Session fixation / login throttling**: no brute-force protection currently
  exists on `/login` — add a simple attempt counter or Spring Security's built-in
  lockout support
- **File access path**: `FileController` correctly checks `canAccessSubmissionFile`,
  which is good — keep this pattern as the template for any new file-serving
  endpoint added in later phases
- **Secrets**: confirm `.env.example` / `application.yml` never has real credentials
  committed (check `.gitignore` covers `.env` — it currently does, good)

### UI/UX (general)
- Consistent empty states: several dashboards likely show blank tables when there's
  no data yet — add friendly empty-state messaging ("No submissions yet — create
  your first one") instead of a bare empty table
- Mobile responsiveness check on `review-split.html` specifically, since split-pane
  layouts are usually the first thing to break on small screens
- Form validation feedback: ensure every form (`new-submission.html`, `register.html`)
  shows inline field errors, not just a generic failure banner

---

## What's deliberately excluded

Multi-university support, knowledge-graph/vector-database infrastructure, citation
tracking across institutions, autonomous agents, policy simulation, and the plugin
marketplace (original Phases 7–18) are not included here. They require a team, real
user volume, and infrastructure a solo project doesn't need. If useful, mention them
as a single "Future Direction" paragraph in your final report — not as build tasks.
