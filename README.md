# UniSubmit

UniSubmit is a Spring Boot academic submission platform for students, lecturers, and administrators. It models a full university hierarchy (faculty → department → programme → unit → curriculum), and combines project/group submission, version history, lecturer review, LLM-backed document analysis, a structured knowledge model (technologies, research areas, frameworks, databases, languages, skills), a multi-signal recommendation engine, lecturer matching, collaboration requests, announcements, notifications, and an append-only audit trail — all in one server-rendered application.

The application is intentionally built as a full-stack Spring Boot + Thymeleaf system. It should be deployed as a Java backend service, not as a static frontend on Vercel.

## Screenshots

### Student Workspace

![Student dashboard](screenshots/student-dashboard.png)
![Submission detail](screenshots/submission-detail.png)
![Project insights](screenshots/project-insights.png)
![Collaboration inbox](screenshots/collaboration-inbox.png)

### Lecturer Review

![Lecturer dashboard](screenshots/lecturer-dashboard.png)
![Lecturer review workspace](screenshots/lecturer-review-workspace.png)

### Administration

![Admin user management](screenshots/admin-user-management.png)
![Admin activity overview](screenshots/admin-activity-overview.png)

## Features

### Academic structure
- Full hierarchy: Faculty → Department → Programme → Unit → Curriculum, with Academic Year / Semester scoping and admin CRUD for every level.
- Group projects (`ProjectGroup`) with a leader and members, alongside individual submissions.
- Multiple supervisors per submission and per-curriculum teaching assignments.
- Project lifecycle statuses (`DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED`, plus `PROPOSAL/FINAL/ARCHIVED`) with deadline enforcement per unit.

### Document intelligence
- Apache Tika text extraction with an optional GROBID pipeline for structured PDF parsing (introduction/methodology/conclusion + reference extraction).
- LLM-backed analysis (OpenAI-compatible, defaults to OpenRouter) producing a strict JSON contract: summary, keywords, objectives, problem statement, technologies, and research areas — with a deterministic local-heuristic fallback if the LLM call fails.
- Structured output is mapped onto the knowledge-model lookup tables (`Technology`, `ResearchArea`, `Framework`, `Database`, `ProgrammingLanguage`, `Skill`) instead of stored as free text, with admin screens to manage/merge tags.
- Optional SPECTER2 embedding service for semantic similarity between submissions (pgvector-backed).

### Recommendation engine (Phase 5)
- Multi-signal similarity scoring blending free-text keyword overlap, title similarity, unit/department proximity, embedding cosine similarity, and structured Technology/ResearchArea tag overlap — weights are configurable via `application.yml`, not hardcoded.
- Every match stores a full per-signal breakdown so the UI can show "why this match" instead of a single opaque score.
- Lecturer matching: recommends reviewers based on the technologies/research areas of submissions they have previously reviewed — pure SQL/Java aggregation, no LLM involved.
- Discovery-level access control: recommendation candidate pools are filtered against submission visibility rules so a student never sees a match that exposes another student's private draft.

### Collaboration & communication
- Collaboration request inbox (incoming, outgoing, accepted) tied directly into the recommendation engine's similar-work results.
- Lecturer announcements and assignment notices per unit, with automatic deadline propagation and in-app notification fan-out to enrolled/programme students.
- In-app notification bell with unread counts for feedback, status changes, and deadlines.

### Academic memory
- Append-only audit log (status changes, feedback, uploads) rendered as a visual timeline on each project page.
- Curated `SubmissionRelation` links (inspired-by / extends / related) for manual research lineage, kept distinct from the automatic similarity engine.

### Platform
- Student registration, sign-in, project creation, and version uploads with file-type/size validation.
- Lecturer review queues grouped by unit, with feedback, grading, and status decisions.
- Admin console for users, roles, student/staff identifiers, faculties, departments, programmes, units, curricula, lecturer assignments, and knowledge-model tags.
- PostgreSQL persistence (with pgvector) and Flyway-managed schema migrations; file uploads stored on disk or a mounted volume.
- Docker and Docker Compose support, including optional GROBID and SPECTER services for local development.

## Architecture

UniSubmit is a conventional layered Spring application:

- `controller` / `controller.admin`: Spring MVC routes for auth, student, lecturer, admin, projects, groups, files, AI insight API, and health checks.
- `service`: business workflows — submissions, access control, AI analysis (`AIInsightProcessingService`), recommendations (`RecommendationService`, `LecturerRecommendationService`), academic hierarchy, groups, announcements, notifications, and audit logging.
- `repository`: Spring Data JPA repositories.
- `domain`: JPA entities and enums covering the academic hierarchy, knowledge model, and audit/relation tables.
- `db/migration`: Flyway SQL migrations (`V2`–`V16`) tracking schema evolution from the flat original model to the current academic/knowledge-model/recommendation schema.
- `templates`: Thymeleaf pages and shared fragments (`fragments/components.html` holds the AI insight panel, similarity/lecturer-match widgets, and timeline components).
- `static`: global CSS design system (`base.css`), JavaScript, and favicon assets.

The UI is server-rendered with Thymeleaf and enhanced with a small amount of dependency-free JavaScript for navigation, filtering, AI polling, review actions, table search, and confirmations.

## Tech Stack

- Java 17
- Spring Boot 4
- Spring MVC, Spring Security, Spring Data JPA
- Thymeleaf
- PostgreSQL + pgvector
- Flyway
- Apache Tika (with optional GROBID for structured academic PDF parsing)
- OpenAI-compatible LLM client (OpenRouter by default) for document analysis
- Optional SPECTER2 microservice for semantic embeddings
- Maven
- Docker

## Local Development

### Option 1: Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Open:

```text
http://localhost:8080
```

Docker Compose starts PostgreSQL, waits for it to become healthy, then starts UniSubmit with persistent Docker volumes for the database and uploads.

### Option 2: Maven + Local PostgreSQL

Create a PostgreSQL database named `unisubmit`, then run:

```bash
cp .env.example .env
```

Set the environment variables from `.env.example`, then start the app:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
$env:PGHOST="localhost"
$env:PGPORT="5432"
$env:PGDATABASE="unisubmit"
$env:PGUSER="unisubmit"
$env:PGPASSWORD="unisubmit"
$env:APP_UPLOAD_DIR="uploads"
.\mvnw.cmd spring-boot:run
```

### Option 3: Maven + H2 (no PostgreSQL required)

For quick local iteration without Docker or PostgreSQL, run with the `local` profile. It uses an embedded, file-backed H2 database (`unisubmit-db.mv.db` in the project root) and disables Flyway in favour of Hibernate `ddl-auto: update`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

On first run, `UnisubmitApplication` seeds three demo accounts (all `password123`): admin username `admin`, lecturer staff ID `L001`, and student ID `S001`, plus starter technology/research-area/framework/database/language/skill lookup rows. This profile is for local development only — it is never used in production or Docker deployments.

## Environment Variables

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `PORT` | No | `8080` | HTTP port used by Spring Boot. |
| `PGHOST` | No | `localhost` | PostgreSQL host when not using `JDBC_DATABASE_URL`. |
| `PGPORT` | No | `5432` | PostgreSQL port. |
| `PGDATABASE` | No | `unisubmit` | PostgreSQL database name. |
| `PGUSER` | No | `unisubmit` | PostgreSQL username. |
| `PGPASSWORD` | No | `unisubmit` | PostgreSQL password. |
| `JDBC_DATABASE_URL` | No | built from `PG*` vars | Full JDBC URL for managed PostgreSQL providers. |
| `APP_UPLOAD_DIR` | No | `uploads` | Directory where uploaded files are stored. |
| `DB_MAX_POOL_SIZE` | No | `5` | Maximum Hikari database connections. |
| `DB_MIN_IDLE` | No | `1` | Minimum idle Hikari connections. |
| `DB_CONNECTION_TIMEOUT_MS` | No | `30000` | Database connection timeout in milliseconds. |
| `OPENAI_API_KEY` | Yes, for AI analysis | — | API key for the OpenAI-compatible LLM used to summarise submissions. Without it, analysis falls back to a local keyword/heuristic summary. |
| `OPENAI_BASE_URL` | No | `https://openrouter.ai/api/v1` | Base URL for the OpenAI-compatible chat completions endpoint. |
| `OPENAI_MODEL` | No | `openai/gpt-4o-mini` | Model identifier passed to the LLM provider. |
| `GROBID_ENABLED` | No | `false` | Enables structured academic PDF parsing (introduction/methodology/conclusion + references) via a GROBID service. |
| `GROBID_URL` | No | `http://localhost:8070` | URL of the GROBID service (see `docker-compose.yml`). |
| `SPECTER_ENABLED` | No | `false` | Enables SPECTER2 embedding generation for semantic similarity scoring. |
| `SPECTER_URL` | No | `http://localhost:5001` | URL of the SPECTER embedding microservice (see `specter-service/`). |
| `unisubmit.recommendation.weight.*` | No | see `application.yml` | Relative weights (keyword, title, unit, semantic, technology, research-area) for the recommendation engine's blended score — tune per demo/report without a code change. |

## Supabase PostgreSQL

Use Supabase as the PostgreSQL provider and deploy UniSubmit on a Java-capable host such as Render, Railway, Fly.io, a VM, or any Docker platform.

For Render, prefer Supabase's **Session pooler** connection string unless you have confirmed your Render service can reach Supabase's IPv6-only direct host or you have enabled Supabase's IPv4 add-on. Do not use the Transaction pooler for this app unless you also disable prepared statements at the JDBC/Hibernate layer.

Set these variables on Render:

```env
JDBC_DATABASE_URL=jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres?sslmode=require
PGUSER=postgres.your-project-ref
PGPASSWORD=your-supabase-password
APP_UPLOAD_DIR=/tmp/uploads
DB_MAX_POOL_SIZE=5
DB_MIN_IDLE=1
```

Do not deploy this application to Vercel as a static site. UniSubmit is not a separate frontend bundle; it is a server-rendered Spring Boot application.

## Render Deployment

This repository includes `render.yaml` for a Docker-based Render Web Service. It configures:

- Docker runtime using the root `Dockerfile`
- HTTP health check at `/health`
- `PORT=8080`
- temporary upload storage at `/tmp/uploads`
- Supabase database variables entered as secrets

Render persistent disks require a paid web-service plan. The checked-in Blueprint avoids disks so it can deploy without billing details. Uploaded files will be lost whenever the free service restarts or redeploys. When billing is available, add a persistent disk mounted at `/app/uploads` and change `APP_UPLOAD_DIR` to `/app/uploads`.

## Docker Deployment

Build the production image:

```bash
docker build -t unisubmit .
```

Run it with PostgreSQL variables:

```bash
docker run --rm -p 8080:8080 \
  -e JDBC_DATABASE_URL="jdbc:postgresql://host:5432/unisubmit?sslmode=require" \
  -e PGUSER="postgres" \
  -e PGPASSWORD="password" \
  -e APP_UPLOAD_DIR="/app/uploads" \
  -v unisubmit-uploads:/app/uploads \
  unisubmit
```

Uploaded files should be backed by a persistent volume in production.

## Testing And Verification

Compile the project:

```bash
./mvnw compile
```

Run tests:

```bash
./mvnw test
```

Some integration tests may require a reachable PostgreSQL database, depending on the active test configuration.

## Roadmap

Current build implements the "Feasible Roadmap" through Phase 5 (recommendation engine expansion). See `UniSubmit-Feasible-Roadmap.md` for full phase details. Remaining/open items:

- Phase 6 — Explainable Academic Assistant: an LLM endpoint that narrates the already-computed recommendation/DNA data (never computes its own similarity), plus a scoped Q&A box limited to a submission's own extracted text.
- First-class pagination and server-side search for large admin datasets.
- Rubric-aware AI review support once backend fields are available.
- Object storage support for uploaded files (currently disk/volume-backed).
- Screenshot-based UI regression tests for lecturer and admin workflows.
- Login throttling / brute-force protection on `/login`.

Multi-university support, knowledge-graph/vector-database infrastructure beyond pgvector, citation tracking across institutions, autonomous agents, and a plugin marketplace are deliberately out of scope for this solo-buildable project.

## Contributing

1. Create a branch for your change.
2. Keep business logic changes separate from presentation changes.
3. Run `./mvnw compile` before opening a pull request.
4. Include screenshots for UI changes.
5. Document any new environment variables.

## License

No license has been declared yet. Add one before distributing or accepting external contributions.
