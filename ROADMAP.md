# UniSubmit → A+ · Execution Roadmap & Tracker

> **For any Claude session (Fable 5 or Opus) picking this up cold.** The owner may lose
> Fable access mid-run — this file is the handoff. Work top-down within your lane,
> tick checkboxes as you land items, keep the build green after every phase.

## Session bootstrap (read this first)
1. Read `FABLE5-HANDOFF.md` **§3 (hard constraints)** and **§6 (load-bearing JS hooks + CSRF contract)**. Non-negotiable.
2. Build check: `JAVA_HOME=C:\Users\mbuya\.jdks\jdk-17.0.19+10`, then `.\mvnw.cmd -B -ntp -DskipTests package` from repo root → must end BUILD SUCCESS.
3. **Never** `git push` (owner pushes). Commit is OK if asked. **Never** stage `*.apk`, `*.aab`, `signing*`, `boot-*.log`, `Readme.html`, `assetlinks.json` (Android signing secrets live in the repo folder — they must not enter git).
4. If you change the shape of `base.css` / `app.js` / icons, **bump `VERSION` in `static/sw.js`** (currently v3 → next v4) or phones serve stale assets.
5. Update THIS file's checkboxes + the log at the bottom before you finish.

## Lanes
- **FABLE (design/frontend):** F-phases. Templates, base.css, app.js (additive only), icons.
- **OPUS (backend/debug):** O-phases. Java, application.yml, pom.xml, data.
- **EITHER:** Q-phases.

## Grade targets
| Area | Now | Target | Phase |
|---|---|---|---|
| Mobile | C+ | **A** | F1 |
| Desktop | A− | **A** | F2 + Q1 |
| Architecture | B+ | **A** | O2 |
| Adoption (CSV) | — | **A** | O1 |
| Feature scope | A | A+ (optional) | F3 |

---

## Phase F1 — Mobile mega-pass (FABLE · TONIGHT · highest priority)

- [x] **F1.1 Bottom tab bar.** New fragment in `fragments/navbar.html` (e.g. `bottomNav`), included from `layout.html` for authenticated users only. Class names: `bottom-nav`, links `nav-link bottom-nav__link` (reusing `nav-link` is FORBIDDEN inside `.nav` only — here it's a new parent, but to get active-marking extend the selector in `app.js` `markActiveNav` to `".nav .nav-link, .bottom-nav a"` — additive edit). Role links (max 4, `sec:authorize`):
  - STUDENT: Projects `/student/dashboard` · Announcements `/student/announcements` · Inbox `/student/inbox` · Explore `/explore`
  - LECTURER: Review `/lecturer/dashboard` · Announcements `/lecturer/announcements` · Explore `/explore`
  - ADMIN: Admin `/admin/dashboard` · Explore `/explore` · Notifications `/notifications`
  Small inline 16px outline SVG icons + 11px labels. CSS in `base.css`: hidden by default; `@media (max-width:860px)` → fixed bottom, `display:flex`, surface bg + top hairline, `padding-bottom: env(safe-area-inset-bottom)` (APK gesture bar), active link = `--primary` tint. Add `body { padding-bottom: calc(60px + env(safe-area-inset-bottom)) }` and `display:none` the `.app-footer` at ≤860px (bottom nav supersedes it).
- [x] **F1.2 View transitions.** In `base.css`: `@media (prefers-reduced-motion: no-preference) { @view-transition { navigation: auto; } }` — cross-document fade in Chrome/APK, kills the white flash. One rule, progressive enhancement.
- [x] **F1.3 Prefetch (speculation rules).** (also added to admin/layout.html) In `layout.html` head: one `<script type="speculationrules">` with `prefetch`, `eagerness: "moderate"`, `href_matches` allowlist ONLY (no side-effect GETs): `/student/*`, `/lecturer/*`, `/explore*`, `/groups*`, `/discover*`. Do NOT include `/logout`, `/notifications*` (view may mark-as-read), `/files/*`, `/admin/*` (heavy tables).
- [x] **F1.4 Mobile density block** (landed by earlier session) in `base.css` ≤860px: serif `h1` → ~1.3rem; `.card` and `.card-stack > .card-section` padding → ~0.9rem 1rem; filter chips row (`lecturer/dashboard.html` STATUS/TYPE rows + student dashboard filters) → single horizontal scroll line: `display:flex; flex-wrap:nowrap; overflow-x:auto; scrollbar-width:none`, drop/sr-only the "Status"/"Type" label words on mobile.
- [x] **F1.5 Lecturer table → list rows** (landed by earlier session; chip-flow design, no data-labels needed) at ≤640px. `lecturer/dashboard.html` submissions table: add `data-label` attrs on `<td>`s; CSS utility `.table-stack` — `thead{display:none}`, `tr{display:block; border-bottom:1px solid var(--border-muted); padding:.6rem 0}`, `td{display:flex; gap:.5rem; border:none; padding:.15rem 0}`, `td::before{content:attr(data-label); color:var(--text-subtle); font-size:.7rem; min-width:84px}`. Apply same class to admin `accounts.html` table if time.
- [x] **F1.6 SW bump** → `unisubmit-shell-v4` done.
- [~] **F1.7 Build** — owner skipped local build (no Java touched this round; Railway builds on push).

**Done-when:** at 390px — bottom bar present with active state, page fades between navigations, lecturer queue reads as stacked rows not a squeezed table, filters are one thumb-scrollable line, nothing overflows horizontally. Desktop (≥861px) pixel-identical to before.

## Phase F2 — Desktop polish to A (FABLE · if time tonight)
- [ ] **F2.1 Self-host fonts.** Download woff2: Inter 400/500/600/700 + Fraunces variable (opsz 9..144, wght 500–700) → `static/fonts/`. `@font-face` block at top of `base.css` (`font-display: swap`), remove the two Google `<link>`s from `layout.html` (and `admin/layout.html` if it has its own head). Removes the last CDN dependency + the mobile font-flash. Bump SW version again if F1 already shipped.
- [ ] **F2.2 Consistency sweep.** Visible `:focus-visible` ring on all interactive elements (buttons, chips, nav links, table rows); empty states all follow one pattern (icon optional, ONE line, muted); check every page's `page-head` for stray subtitles that survived the de-clutter.

## Phase F3 — OPTIONAL flourish (FABLE · only if tokens remain)
- [ ] "Why this match" visual: tiny horizontal signal bars (existing `.signal-row` data) in match cards — makes the matching engine legible. No new data needed, presentation only.

## Phase O1 — CSV bulk import (OPUS · anytime, owner's flagged priority)
- [ ] **O1.1** Add `org.apache.commons:commons-csv:1.10.0` to `pom.xml`.
- [ ] **O1.2** `CsvImportService` (new, `service/`): two importers.
  - **Students:** columns `name,email,studentId,programmeCode,year`. Per-row validation (email format, unique email/studentId, programme exists by code). Generates a random 10-char password per row (`SecureRandom`, alphanumeric, no ambiguous chars).
  - **Academic structure:** columns `facultyCode,facultyName,departmentCode,departmentName,programmeCode,programmeName,unitCode,unitName`. Auto-create missing parents by code (idempotent: existing codes = skip, never mutate names of existing rows).
- [ ] **O1.3** `AdminImportController` (`controller/admin/`): `GET /admin/import` (page), `POST /admin/import/preview` (multipart → parse → session-stash parsed rows → render preview: green valid / red invalid with reason per row), `POST /admin/import/apply` (transactional apply of the STASHED rows — never re-parse the file on apply), `GET /admin/import/template/{students|structure}` (CSV template download), `GET /admin/import/results` (post-apply credentials CSV: `name,studentId,email,password` — generated once, downloadable once, never persisted in plaintext).
- [ ] **O1.4** `templates/admin/import.html` — upload card, preview table (reuse `.table` + badges), confirm button with `data-loading-submit`. Add "Import" link to the admin sidebar fragment. Follow the de-cluttered voice: one line of guidance max.
- [ ] **O1.5** Row cap 2000, reject non-CSV content types, wrap apply in one transaction, audit-log the import (existing `AuditLogRepository` pattern). Build green + a happy-path and a bad-file manual test note for the owner.

## Phase O2 — Architecture to A (OPUS)
- [ ] **O2.1 Spring Session JDBC:** add `spring-session-jdbc` dependency; `spring.session.jdbc.initialize-schema=always` (+ `spring.session.timeout=4h`). Sessions survive redeploys. Verify login → redeploy → still signed in.
- [ ] **O2.2 Demo seeder OFF in prod:** `application.yml` → remove/false `unisubmit.demo.seed-collaboration` (absence = off via `@ConditionalOnProperty`); keep `true` only in `application-local.yml`. Existing demo rows in prod: leave; owner decides cleanup later.
- [ ] **O2.3 Static asset caching:** `spring.web.resources.cache.cachecontrol.max-age=30d` + content-hash chain strategy (`spring.web.resources.chain.strategy.content.enabled=true`, paths `/css/**,/js/**,/icons/**,/fonts/**`). Thymeleaf `@{}` URLs pick up hashed names automatically. `sw.js` stays fresh (browsers cap SW script cache at 24h by spec — no action).
- [ ] **O2.4** Leave `ddl-auto=update` + Flyway disabled for now (deliberate; see application.yml comment). Post-testing: Flyway re-adopt with `baseline-version=19`.

## Phase Q1 — Quick wins (EITHER)
- [ ] **Q1.1** `assetlinks.json` → serve at `static/.well-known/assetlinks.json` (copy the file the owner has in repo root; needed for the APK to verify → removes the browser URL bar in the TWA). Confirm `SecurityConfig` already permits `/.well-known/assetlinks.json` (it does).
- [ ] **Q1.2** Add the untracked junk to `.gitignore`: `*.apk`, `*.aab`, `signing*`, `boot-*.log`, `Readme.html`, root `assetlinks.json`.

## Later (post-testing · NOT now)
Forced password change on first login + delete demo accounts · Flyway baseline re-adopt · SSO · backups/paid tier · RLS enable on Supabase (needs one MCP call once tables stable).

---

## Log (append a line per session)
- 2026-07-13 ~03:00 Fable: roadmap created; starting F1.
- 2026-07-13 ~03:30 Fable: F1 COMPLETE (bottom nav + view transitions + prefetch + SW v4; density/table-stack found already landed). Next: F2 fonts, or O1 CSV (Opus). NOTE for Q-list: admin/layout.html loads Chart.js from jsdelivr CDN — self-host alongside fonts.
