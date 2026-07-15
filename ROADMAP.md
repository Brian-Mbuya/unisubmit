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
- [x] **F2.1 Self-host fonts.** DONE: `static/fonts/inter-latin.woff2` (48KB) + `fraunces-latin.woff2` (67KB), variable weights; `@font-face` at top of base.css; Google `<link>`s removed from both layouts; `/fonts/**` added to SecurityConfig permitAll; SW prefixes + VERSION → v5. Original spec: Download woff2: Inter 400/500/600/700 + Fraunces variable (opsz 9..144, wght 500–700) → `static/fonts/`. `@font-face` block at top of `base.css` (`font-display: swap`), remove the two Google `<link>`s from `layout.html` (and `admin/layout.html` if it has its own head). Removes the last CDN dependency + the mobile font-flash. Bump SW version again if F1 already shipped.
- [x] **F2.2 Consistency sweep.** DONE (baseline `:focus-visible` ring added §27; `.btn`/`.form-control` keep their own richer treatments; empty states were already normalized by the de-clutter pass). Original spec: Visible `:focus-visible` ring on all interactive elements (buttons, chips, nav links, table rows); empty states all follow one pattern (icon optional, ONE line, muted); check every page's `page-head` for stray subtitles that survived the de-clutter.

## Phase F3 — OPTIONAL flourish (FABLE · only if tokens remain)
- [x] "Why this match" visual — DONE. Bars already existed (`.signal-row` in `components.html`); F3 made them honest: zero-score rows hidden (no more "Semantic (AI) 0%" on every card while the LLM key is lean), and the "Same unit" bar replaced by a one-line context note ("context only, not scored") to match the RecommendationService retention philosophy. Build green.

## Phase O1 — CSV bulk import (OPUS · DONE — students importer shipped)
> Students importer complete & build-green. Academic-structure importer (O1.2 second
> bullet) deferred as a clean follow-up — service is structured to add it alongside.
> Deviations from spec: apply is per-row (not one transaction) so one bad row can't
> discard a good batch — preview already validates, so partial failure is rare and each
> row is atomic via UserService.createUser's own @Transactional. DB audit-log skipped
> (audit_logs.submission_id is NOT NULL; import isn't submission-scoped) — logs via SLF4J.
- [x] **O1.1** commons-csv 1.11.0 added to `pom.xml`.
- [x] **O1.2** `CsvImportService` — students importer done (parse+validate no-writes, per-row apply, SecureRandom 10-char unambiguous passwords, CourseRepository.findByCodeIgnoreCase added). Academic-structure importer: TODO follow-up.
  - **Students:** columns `name,email,studentId,programmeCode,year`. Per-row validation (email format, unique email/studentId, programme exists by code). Generates a random 10-char password per row (`SecureRandom`, alphanumeric, no ambiguous chars).
  - **Academic structure:** columns `facultyCode,facultyName,departmentCode,departmentName,programmeCode,programmeName,unitCode,unitName`. Auto-create missing parents by code (idempotent: existing codes = skip, never mutate names of existing rows).
- [x] **O1.3** `AdminImportController` — page/preview/apply/results + results.csv + template.csv, session-stashed rows (records are Serializable, ready for O2 JDBC sessions). Route note: results CSV is `/admin/import/students/results.csv`.
- [x] **O1.3b** (was O1.3) (`controller/admin/`): `GET /admin/import` (page), `POST /admin/import/preview` (multipart → parse → session-stash parsed rows → render preview: green valid / red invalid with reason per row), `POST /admin/import/apply` (transactional apply of the STASHED rows — never re-parse the file on apply), `GET /admin/import/template/{students|structure}` (CSV template download), `GET /admin/import/results` (post-apply credentials CSV: `name,studentId,email,password` — generated once, downloadable once, never persisted in plaintext).
- [ ] **O1.4** `templates/admin/import.html` — upload card, preview table (reuse `.table` + badges), confirm button with `data-loading-submit`. Add "Import" link to the admin sidebar fragment. Follow the de-cluttered voice: one line of guidance max.
- [ ] **O1.5** Row cap 2000, reject non-CSV content types, wrap apply in one transaction, audit-log the import (existing `AuditLogRepository` pattern). Build green + a happy-path and a bad-file manual test note for the owner.

## Phase O1 — CSV bulk import — ✅ DONE (students importer, committed)
AdminImportController + CsvImportService + admin/import.html + commons-csv all present and build-green.
Full flow: upload → validated preview → apply → one-time credentials CSV + template download.
Academic-STRUCTURE importer (faculties/depts/programmes/units by code) NOT built — optional future add;
needs `findByCodeIgnoreCase` on Faculty/Department repos + `findByUnitCodeIgnoreCase` on Unit repo.

## Phase O2 — Architecture to A (OPUS)
> STATUS (2026-07-13): O2.2 done as an env toggle. O2.1 + O2.3 DEFERRED on purpose — both
> carry live-app risk during active testing (see notes). Do them in a maintenance window, not mid-test.
- [x] **O2.2 Demo seeder OFF in prod (toggle).** `application.yml` → `seed-collaboration: ${DEMO_SEED_COLLABORATION:false}` (default off; set Railway env `DEMO_SEED_COLLABORATION=true` to re-enable for a pitch). Local stays on. Existing demo rows remain until cleaned.
- [⛔] **O2.1 Spring Session JDBC — ATTEMPTED, BACKED OUT (hard blocker).** The self-healing
  schema part was solved (idempotent `session-schema-postgresql.sql`), BUT the real blocker is
  serialization: `CustomUserDetails` (not Serializable) wraps the `User` **JPA entity** (not
  Serializable, lazy relationships). JDBC sessions serialize the security context → it would throw
  `NotSerializableException` and **break login for everyone**. Making the entity graph Serializable
  is a JPA anti-pattern. PROPER FIX (a real task, not a config toggle): introduce a lightweight
  `record`-based serializable principal (id, username, name, role, profileIds) and refactor the
  ~dozen `userDetails.getUser()` call-sites to fetch the entity fresh when they need relationships.
  Until then, in-memory sessions stay (cost: a redeploy signs users out — an annoyance, not a bug).
  Original spec below.
- [ ] ~~O2.1 Spring Session JDBC — DEFERRED (risk).~~ `initialize-schema=always` runs `CREATE TABLE SPRING_SESSION` (no IF NOT EXISTS) → fatal on 2nd boot = crash-loop, same failure mode as the Flyway incident. SAFE recipe: pre-create the `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` tables once via Supabase SQL (script: `org/springframework/session/jdbc/schema-postgresql.sql`), THEN add `spring-session-jdbc` with `spring.session.jdbc.initialize-schema=never` + `spring.session.timeout=4h`. Do in a maintenance window; verify boot before relying on it.
- [ ] **O2.1 Spring Session JDBC:** add `spring-session-jdbc` dependency; `spring.session.jdbc.initialize-schema=always` (+ `spring.session.timeout=4h`). Sessions survive redeploys. Verify login → redeploy → still signed in.
- [ ] **O2.2 Demo seeder OFF in prod:** `application.yml` → remove/false `unisubmit.demo.seed-collaboration` (absence = off via `@ConditionalOnProperty`); keep `true` only in `application-local.yml`. Existing demo rows in prod: leave; owner decides cleanup later.
- [x] **O2.3 Static asset caching — DONE (SW-safe).** `spring.web.resources.cache.cachecontrol.max-age=7d`
  + `cache-public`. Made safe with the SW instead of content-hashing: the SW now precaches with
  `{cache:'reload'}` (install can't seed a stale shell from the HTTP cache) and revalidates with
  `{cache:'no-cache'}` (background refresh always checks the server), so a long HTTP cache can never
  pin stale assets — freshness still flows through the SW version bump (now v7). Original spec:
      **O2.3 Static asset caching:** `spring.web.resources.cache.cachecontrol.max-age=30d` + content-hash chain strategy (`spring.web.resources.chain.strategy.content.enabled=true`, paths `/css/**,/js/**,/icons/**,/fonts/**`). Thymeleaf `@{}` URLs pick up hashed names automatically. `sw.js` stays fresh (browsers cap SW script cache at 24h by spec — no action).
- [ ] **O2.4** Leave `ddl-auto=update` + Flyway disabled for now (deliberate; see application.yml comment). Post-testing: Flyway re-adopt with `baseline-version=19`.

## Phase Q1 — Quick wins (EITHER)
- [ ] **Q1.1** `assetlinks.json` → serve at `static/.well-known/assetlinks.json` (copy the file the owner has in repo root; needed for the APK to verify → removes the browser URL bar in the TWA). Confirm `SecurityConfig` already permits `/.well-known/assetlinks.json` (it does).
- [ ] **Q1.2** Add the untracked junk to `.gitignore`: `*.apk`, `*.aab`, `signing*`, `boot-*.log`, `Readme.html`, root `assetlinks.json`.

## Later (post-testing · NOT now)
Forced password change on first login + delete demo accounts · Flyway baseline re-adopt · SSO · backups/paid tier · RLS enable on Supabase (needs one MCP call once tables stable).

---

- [x] **Q1.1** DONE — `assetlinks.json` copied to `static/.well-known/` (APK verifies → URL bar disappears on next app open after deploy).
- [x] **Q1.2** DONE — `.gitignore` now blocks `*.apk`, `*.aab`, `signing*`, logs; the `.well-known` copy is explicitly allowed.
- [x] **Q1.3** DONE — Chart.js self-hosted (`static/js/vendor/chart.umd.min.js`, pinned 4.4.3). **The app now has ZERO external dependencies.**
- [x] **BONUS (go-wild batch):** manifest app **shortcuts** (long-press icon → New submission / Notifications / Explore) + `categories`/`lang`; student-facing no-key message de-jargoned ("give your project a name and continue"); public **`/about` pitch page** (AuthController `@GetMapping("/about")` → `templates/about.html`, added to permitAll, linked from login footer) for the university pitch. SW → **v6**.

---

## Phase M — Mobile platform, GitHub-app grade (OPUS · designed by Fable, execute top-down)

> **How to work this phase (Opus):** every design decision below is already made — do not
> re-decide, re-style, or "improve" beyond spec. Read handoff §3/§6 first. All CSS goes in
> `base.css` (new §29+, mobile rules inside `@media (max-width: 860px)` unless stated).
> All JS additions are NEW additive functions in `app.js` registered at the end of the
> DOMContentLoaded list — never modify existing functions beyond what a spec says.
> **Bump `sw.js` VERSION once per shipped batch** (next: v9). Build green after every item.
> Ship order = listed order (impact ÷ effort, dependencies respected).

- [x] **M1 · Slim the mobile top bar.** DONE by Fable (base.css §29). Original spec: *(GitHub app: top bar = context + avatar, nothing else.)*
  With the bottom tab bar live, the topbar's Collaboration-inbox icon is a duplicate of the Inbox
  tab. At ≤860px: hide `.header-link[aria-label="Collaboration inbox"]` (CSS only). Keep bell +
  avatar + hamburger. Done-when: one less icon at 390px; desktop unchanged.

- [x] **M2 · Search from the top bar on phones.** DONE by Fable (navbar.html `header-search-link` + a students-only `header-new-link` "+" for one-tap New submission — GitHub-app pattern, bonus beyond spec). Original spec: *(GitHub app: search is one tap, always.)*
  The global search input is `display:none` ≤860 with no replacement. Add ONE anchor in
  `fragments/navbar.html` inside `.topbar-right`, before the bell: class `header-link header-search-link`,
  `aria-label="Search"`, `th:href="@{/explore(tab='search')}"`, reusing the existing 16px magnifier
  SVG path (it's already in the file). CSS: hidden by default, `display:inline-flex` ≤860px.
  Done-when: tap magnifier → Explore search page; not present on desktop.

- [ ] **M3 · Stacked rows for ALL admin tables.** *(GitHub app: everything is a list row.)*
  `accounts.html` + lecturer dashboard already use `.table-stack`. Mechanically apply the same
  pattern to the tables in `admin/{departments,faculties,programmes,units,curricula,assignments,
  tags,evaluation}.html`: add `table-stack` to each `.table-wrap`, put class `stack-full` on the
  title-ish first cell if the table's first column isn't already the identity. Verify each at 390px:
  no horizontal scroll, action buttons reachable. Done-when: zero x-scroll on every admin page.

- [ ] **M4 · Sticky decision bar on the review page.** *(GitHub app: PR verdict is always in reach.)*
  On `lecturer/review-split.html`, at ≤860px the decision buttons end up far down a long scroll.
  CSS-only: make the review form's action row (`.review-actions`) `position:sticky; bottom:calc(58px +
  env(safe-area-inset-bottom)); z-index:30; background:var(--surface-solid); padding:.6rem 0;
  border-top:1px solid var(--border-muted)` inside its card, ≤860 only. Do NOT move DOM (hooks:
  `[data-review-form]`, `[data-review-action]`, `[data-review-status]` untouched). Done-when: at
  390px the Approve/Changes buttons stay visible above the bottom nav while scrolling the document.

- [ ] **M5 · Flash messages become toasts on phones.** *(Native apps confirm at the thumb, not the masthead.)*
  Flash alerts (`fragments/alerts.html` renders `.alert` at content top) are missed on long pages.
  New additive `initMobileToasts()` in app.js: if `window.matchMedia('(max-width:860px)').matches`,
  find `.alert` elements inside `#mainContent` that came from flash (they're the first children),
  add class `toast-mode`, and auto-dismiss success/info variants after 4s (danger stays until tapped;
  tap dismisses any). CSS: `.alert.toast-mode { position:fixed; left:12px; right:12px;
  bottom:calc(70px + env(safe-area-inset-bottom)); z-index:60; box-shadow:var(--shadow-pop); }`
  + reduced-motion-safe fade. Done-when: submit a form on a phone → confirmation appears just above
  the bottom nav and fades; errors persist until tapped; desktop rendering unchanged.

- [x] **M6 · Touch feedback.** DONE by Fable (base.css §29, hover:none + reduced-motion gated). Original spec: *(Apps acknowledge every touch.)* CSS only, global (not media-gated):
  `@media (hover:none){ .btn:active, .filter-btn:active, .list-row:active, .bottom-nav__link:active,
  .recommendation-card:active { transform:scale(.985); transition:transform .05s } }` — plus disable
  iOS tap highlight (`-webkit-tap-highlight-color: transparent` on those selectors). Respect
  reduced-motion (wrap transform in the no-preference guard). Done-when: taps visibly respond in the APK.

- [ ] **M7 · Real page titles.** *(Task-switcher currently shows five tabs all named "UniSubmit".)*
  Additive one-liner in app.js DOMContentLoaded: if `#mainContent h1` exists, set
  `document.title = h1.textContent.trim() + " · UniSubmit"`. Done-when: Android task switcher and
  browser history show "Review queue · UniSubmit" etc.

- [ ] **M8 · Native share on project pages.** *(GitHub app: share sheet from any PR/issue.)*
  On `student/submission-detail.html` and `student/project-detail.html` page-head actions: a small
  `btn btn-secondary` with `data-share-title` = the submission title. Additive `initNativeShare()`:
  on click, if `navigator.share` exists → share {title, url:location.href}; else copy URL to
  clipboard and swap the button label to "Link copied ✓" for 1.5s. Hide the button entirely when
  neither API exists. Done-when: phone opens the OS share sheet; desktop copies the link.

- [ ] **M9 · Import spreadsheets, not just CSV (backend).** *(Root cause of the LibreOffice "can't
  find my file" report — pickers hide .ods/.xlsx when accept=.csv.)* Add `org.apache.poi:poi-ooxml:5.2.5`
  to pom. In `CsvImportService`, new `parseStudentsWorkbook(MultipartFile)` reading the FIRST sheet of
  .xlsx: row 0 = headers (case-insensitive match on the same five names), map each data row into the
  EXISTING validation pipeline (extract a private `validateAndAdd(...)` from parseStudents so both
  paths share ALL rules — no duplicated validation). Controller preview endpoint: branch on filename
  ending `.xlsx` (and content-type sniff). Widen the file input accept to `.csv,.xlsx`. Explicitly NOT
  .ods (LibreOffice users: Save As xlsx/csv — hint text already says so). Done-when: the owner's
  LibreOffice sheet saved as .xlsx previews correctly, including its row count; bad .xlsx gives the
  friendly "doesn't look like a valid file" error, never a stack trace.

- [ ] **M10 · Upload as a dropzone.** *(The file input is the app's most important control and it's a
  system default.)* On `student/new-submission.html`: wrap the existing `#file` input in a
  `label.dropzone` (input visually hidden but NOT display:none — keep it focusable/required).
  Dropzone: dashed `--border-strong` border, upload glyph, "Tap to choose your document" + the
  existing format hint inside it, min-height 96px, full-width; on change, show the chosen filename
  inside the zone. Desktop: also accept drag-and-drop (dragover class + drop assigns
  `input.files = e.dataTransfer.files`). CRITICAL: ids `#file`, `#title` and the suggestion-container
  ids are load-bearing (§6) — the input keeps its id and change handler behavior. Done-when: mobile
  shows a big friendly tap target; drag-drop works on desktop; AI title suggestions still fire.

**Precision batch (added after owner's "more, simpler, more precise" — M11–M13):**
Also DONE by Fable inline (base.css §29): breadcrumbs hidden ≤860 (2–3 lines of header rent, info
repeats in subtitle/details), page-head `h1` clamped to 2 lines, **modals → bottom sheets** ≤860
(`.modal-overlay` bottom-aligned, `.modal-card` full-width sheet w/ safe-area padding — DOM/JS untouched).

- [ ] **M11 · Collapse the rail on submission-detail (phones).** *(GitHub app: secondary sections are
  disclosures.)* In `student/submission-detail.html`, wrap the rail's "Version history" and "Suggested
  reviewers" `.card-section` CONTENT in native `<details class="rail-collapse"><summary>…</summary>…</details>`
  (the existing `.card-head h2` text becomes the summary label; keep heading semantics via CSS).
  "Details" section stays always-open. Additive `initRailCollapse()` in app.js: on load, if
  `min-width:861px` set `open = true` on every `.rail-collapse` (desktop unchanged). CSS: style
  `summary` like `.card-head h2` with a chevron, ≥44px tap height. Done-when: at 390px the rail
  reads as two tappable headings; desktop identical to today.
- [ ] **M12 · Dashboard greeting compaction.** Add class `page-head--greeting` to the page-head of
  `student/dashboard.html` + `lecturer/dashboard.html`. CSS ≤640: its `h1` → 1.05rem, margin-bottom
  0.15rem; its `.subtitle` ("N waiting for review" / progress line) becomes the emphasis —
  `color:var(--text); font-weight:600`. Done-when: at 390×760 the first submission row is visible
  without scrolling on both dashboards.
- [ ] **M13 · Mobile spacing constants.** One audit pass, ≤640: `.container` gutter exactly 16px;
  `.card` and `.card-stack > .card-section` padding exactly 14px 16px; `.page-head` margin-bottom
  0.75rem; sibling section gap 0.75rem. Fix any page with double-padding (card inside padded card).
  Done-when: spacing feels uniform across dashboard/detail/explore at 390px; no nested-padding wells.

**Phase-M guardrails recap:** never rename §6 hooks; CSRF hidden inputs stay; all assets self-hosted;
dark low-glare; desktop ≥861px must remain pixel-stable except where a spec says otherwise; owner
pushes to git (never push); bump SW VERSION per batch; build green before reporting.

## Log (append a line per session)
- 2026-07-13 ~03:00 Fable: roadmap created; starting F1.
- 2026-07-13 Fable: F1 complete (bottom nav, view transitions, prefetch, density/table-stack landed across sessions, SW v4).
- 2026-07-13 Fable: F2 complete (self-hosted variable fonts 115KB total, /fonts/** permitAll, SW v5, global :focus-visible). Found Chart.js CDN in admin layout → logged as Q1.3.
- 2026-07-13 Fable: F3 complete (signal bars now hide zero rows; same-unit demoted to context note). ALL FABLE PHASES DONE — remaining work (O1 CSV, O2 architecture, Q1) is Opus-friendly.
- 2026-07-13 Opus: verified O1 (CSV import) already built + green. O2.2 done (demo seeder → env toggle, default off in prod). O2.1 + O2.3 deliberately DEFERRED (live-app risk during testing — recipes noted above). Build green.
- 2026-07-14 Fable: CSV row-vanish diagnosed (file not saved before upload — parser + template verified airtight); preview now shows "N rows in file". Import hint explains Save-As-CSV (.ods/.xlsx hidden by picker — root of the LibreOffice report; real .xlsx support = M9). Paper doc preview shipped (.doc-sheet, ivory on dark desk) — owner approved. Phase M (mobile, 10 items, fully-specified) authored for Opus. SW to bump to v8 with this batch.
- 2026-07-13 Opus: "complete the roadmap" — O2.3 DONE (SW-safe long asset cache, SW v7). O2.1 ATTEMPTED then BACKED OUT: JDBC session storage would serialize the security context, but CustomUserDetails/User aren't Serializable → would break login. Reverted cleanly (pom/yml/schema). Needs a serializable-principal refactor first (logged above). Build green. Roadmap now: F1-F3 ✅, Q1 ✅, O1 ✅, O2.2 ✅, O2.3 ✅, O2.4 ✅ (no-op decision), O2.1 ⛔ blocked.
- 2026-07-13 Fable go-wild: Q1.1/1.2/1.3 done (assetlinks in .well-known, gitignore, Chart.js self-hosted → ZERO external deps), manifest shortcuts, /about page, SW v6.
- 2026-07-13 Opus: O1 students CSV import shipped, build green (service+controller+template+nav, commons-csv). Academic-structure importer left as follow-up. Next: O2.
- 2026-07-13 ~03:30 Fable: F1 COMPLETE (bottom nav + view transitions + prefetch + SW v4; density/table-stack found already landed). Next: F2 fonts, or O1 CSV (Opus). NOTE for Q-list: admin/layout.html loads Chart.js from jsdelivr CDN — self-host alongside fonts.
