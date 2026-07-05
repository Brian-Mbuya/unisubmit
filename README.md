# UniSubmit

An academic project-submission platform with an **intelligence layer**: it doesn't just
store student work, it reads it, classifies it, recommends related work, detects
duplicate submissions, matches students to cross-disciplinary collaborators, and gives
lecturers and admins measurable insight into the whole corpus.

Built as a solo Spring Boot application — server-rendered, dependency-light, and
designed so every "AI" feature degrades gracefully when no external service is
configured.

---

## What it does

**For students**
- Submit projects into a real academic hierarchy (Faculty → Department → Programme →
  Unit → Curriculum), individually or as a project group, with versioned re-uploads and
  per-version change notes.
- Each upload is analysed automatically: summary, keywords, objectives, problem
  statement, application domains, and structured technology/research-area tags.
- Discover related prior work, and — separately — AI-ranked **cross-disciplinary
  collaborators** (a CS traffic-ML project paired with an EE IoT-sensor project, etc.).
- Track reviews, feedback, grades and deadlines; get deadline reminders.

**For lecturers**
- A split-screen review workspace: document preview beside AI insights, similar work,
  activity timeline and the grade/feedback form.
- Suggested reviewers, blind-review mode, and one-click **CSV marks export** per unit.
- Post announcements and assignments with deadlines.

**For admins**
- Full academic-hierarchy and account management, tag curation, password resets.
- **Evaluation** page — measured recommender quality (precision@5, MRR) against real
  accepted collaboration requests, plus collaboration acceptance rate.
- **Research Landscape** — every analysed project clustered (k-means) and projected
  (PCA) into a 2-D topic map.

---

## The intelligence story

The differentiator is that the "smart" features are **explainable and measured**, not a
black box:

1. **Document analysis pipeline** — Apache Tika text extraction → optional GROBID
   structured parsing → an OpenAI-compatible LLM call for summary/keywords/objectives/
   domains → optional SPECTER2 embedding. With no API key it falls back **honestly**
   (TF keywords + extractive summary, never fabricated tags).

2. **Six-signal recommendation engine** — keyword overlap, title similarity,
   unit/department proximity, embedding cosine, and structured Technology/ResearchArea
   Jaccard, blended with configurable weights. Uses **adaptive normalisation**: signals
   that can't fire (no embedding, no keywords) are excluded from the denominator instead
   of silently dragging scores down. Every match ships a per-signal "why this match"
   breakdown.

3. **Integrity check** — byte-identical uploads (SHA-256) are flagged as duplicate
   submissions at 100%, distinct from mere topical similarity.

4. **Collaboration discovery (two-stage)** — a mechanical pre-filter (whole-corpus,
   unit weight zero, cross-department and problem-domain boosted) shortlists ~15
   candidates, then an LLM assesses each pair for value, type (mentorship / skill
   exchange / interdisciplinary / scale-up / data sharing), what each side gains, and a
   natural-language pitch — grounded strictly in the already-extracted data.

5. **Evaluation harness** — accepted collaboration requests are logged ground truth;
   the recommender is replayed under several weight configurations and scored, so
   "I built a recommender" becomes "I built and validated one."

---

## Architecture

```
Upload ─▶ Tika/GROBID ─▶ LLM analysis ─▶ SPECTER embedding
                                │
                                ▼
             Recommendation precompute (6 signals)  ──▶ SubmissionSimilarity
             Collaboration Stage 1 (mechanical)      ──▶ CollaborationMatch (UNASSESSED)
                                │
                                ▼
             Collaboration Stage 2 (LLM assessment)  ──▶ CollaborationMatch (HIGH/MEDIUM…)

Read side: Explore (hybrid BM25 + optional pgvector search, Discover tab),
           Evaluation (precision@5 / MRR), Landscape (k-means + PCA).
```

- **Stack**: Spring Boot 4 (Java 17), Spring Security, Spring Data JPA, Thymeleaf,
  Lombok, Flyway. PostgreSQL + pgvector in production; H2 file DB for local dev.
- **Sidecar** (`specter-service/`): a small Flask app exposing `/embed` (SPECTER2) and
  `/ocr` (Tesseract fallback for scanned PDFs).
- **UI**: server-rendered Thymeleaf, one dark design system in `static/css/base.css`,
  dependency-free JS in `static/js/app.js`.

---

## Running locally

This machine uses a portable JDK; there is no system Java/Maven.

```powershell
# One-liner — finds the JDK itself, loads .env, starts on http://localhost:8080
.\run-local.ps1
```

The `local` profile uses an H2 file DB (Flyway off, `ddl-auto: update`) and seeds demo
accounts (all password **`password123`**):

| Login              | Role     |
|--------------------|----------|
| `admin`            | Admin    |
| `L001` ("lecturer")| Lecturer |
| `S001` ("student") | Student  |

With `unisubmit.demo.seed-collaboration=true` (on by default in the local profile) it
also seeds cross-department demo projects (`D-CS-1`, `D-EE-1`, `D-PH-1`, …) so
collaboration discovery is demonstrable immediately.

**Build / test** (set `JAVA_HOME` to the portable JDK first):

```powershell
$env:JAVA_HOME = "C:\Users\mbuya\.jdks\jdk-17.0.19+10"
.\mvnw.cmd -q compile -DskipTests   # must exit 0
.\mvnw.cmd test                     # 43 unit tests, all green
```

> **Note:** start the app via `run-local.ps1` (or the IDE/preview launch config), which
> loads `.env`. Starting `mvnw spring-boot:run` directly does **not** load `.env`, so the
> AI key is missing and analysis runs in fallback mode.

---

## Configuration flags

Set in `.env` or as environment variables:

| Flag | Default | Effect |
|------|---------|--------|
| `OPENAI_API_KEY` | *(unset)* | Real LLM analysis instead of fallback |
| `OPENAI_MODEL` | `openai/gpt-4o-mini` | LLM model (OpenRouter-compatible) |
| `GROBID_ENABLED` | `false` | Structured PDF parsing via GROBID sidecar |
| `SPECTER_ENABLED` | `false` | SPECTER2 embeddings (semantic signal + landscape) |
| `SEARCH_SEMANTIC_ENABLED` | `false` | pgvector semantic channel in search (Postgres only) |
| `OCR_ENABLED` | `false` | OCR fallback for scanned PDFs |
| `BLIND_REVIEW` | `false` | Hide student identity from lecturers until graded |
| `unisubmit.ai.timeout-seconds` | `120` | Upper bound on one analysis run |

The single biggest quality jump needs zero code: set `OPENAI_API_KEY` and run the
SPECTER sidecar — semantic similarity, the landscape map's real embeddings, and semantic
search all light up at once.

---

## Scale ceilings (honest limits)

Deliberate simplifications suitable for a single-institution deployment of hundreds of
submissions; each would need work at thousands:

- Search, landscape and evaluation iterate the corpus in memory (no search index).
- Login throttling and assistant rate limits are in-memory (single node).
- Collaboration and similarity are precomputed per submission, not incrementally
  streamed.

---

## Build phases

1. Academic foundation · 2. Knowledge model · 3. AI analysis engine · 4. Academic memory
(relations + audit) · 5. Recommendation engine · 6. Explainable assistant · 7. Measured
intelligence (evaluation, hybrid search, landscape, integrity check, OCR, blind review)
· 8. AI collaboration discovery · 9. Hardening & completion.
