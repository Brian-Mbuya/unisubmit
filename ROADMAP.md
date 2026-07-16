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

- [x] **M3 · Stacked rows for ALL admin tables.** *(GitHub app: everything is a list row.)*
  `accounts.html` + lecturer dashboard already use `.table-stack`. Mechanically apply the same
  pattern to the tables in `admin/{departments,faculties,programmes,units,curricula,assignments,
  tags,evaluation}.html`: add `table-stack` to each `.table-wrap`, put class `stack-full` on the
  title-ish first cell if the table's first column isn't already the identity. Verify each at 390px:
  no horizontal scroll, action buttons reachable. Done-when: zero x-scroll on every admin page.

- [x] **M4 · Sticky decision bar on the review page.** *(GitHub app: PR verdict is always in reach.)*
  On `lecturer/review-split.html`, at ≤860px the decision buttons end up far down a long scroll.
  CSS-only: make the review form's action row (`.review-actions`) `position:sticky; bottom:calc(58px +
  env(safe-area-inset-bottom)); z-index:30; background:var(--surface-solid); padding:.6rem 0;
  border-top:1px solid var(--border-muted)` inside its card, ≤860 only. Do NOT move DOM (hooks:
  `[data-review-form]`, `[data-review-action]`, `[data-review-status]` untouched). Done-when: at
  390px the Approve/Changes buttons stay visible above the bottom nav while scrolling the document.

- [x] **M5 · Flash messages become toasts on phones.** *(Native apps confirm at the thumb, not the masthead.)*
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

- [x] **M7 · Real page titles.** *(Task-switcher currently shows five tabs all named "UniSubmit".)*
  Additive one-liner in app.js DOMContentLoaded: if `#mainContent h1` exists, set
  `document.title = h1.textContent.trim() + " · UniSubmit"`. Done-when: Android task switcher and
  browser history show "Review queue · UniSubmit" etc.

- [x] **M8 · Native share on project pages.** *(GitHub app: share sheet from any PR/issue.)*
  On `student/submission-detail.html` and `student/project-detail.html` page-head actions: a small
  `btn btn-secondary` with `data-share-title` = the submission title. Additive `initNativeShare()`:
  on click, if `navigator.share` exists → share {title, url:location.href}; else copy URL to
  clipboard and swap the button label to "Link copied ✓" for 1.5s. Hide the button entirely when
  neither API exists. Done-when: phone opens the OS share sheet; desktop copies the link.

- [x] **M9 · Import spreadsheets, not just CSV (backend).** *(Root cause of the LibreOffice "can't
  find my file" report — pickers hide .ods/.xlsx when accept=.csv.)* Add `org.apache.poi:poi-ooxml:5.2.5`
  to pom. In `CsvImportService`, new `parseStudentsWorkbook(MultipartFile)` reading the FIRST sheet of
  .xlsx: row 0 = headers (case-insensitive match on the same five names), map each data row into the
  EXISTING validation pipeline (extract a private `validateAndAdd(...)` from parseStudents so both
  paths share ALL rules — no duplicated validation). Controller preview endpoint: branch on filename
  ending `.xlsx` (and content-type sniff). Widen the file input accept to `.csv,.xlsx`. Explicitly NOT
  .ods (LibreOffice users: Save As xlsx/csv — hint text already says so). Done-when: the owner's
  LibreOffice sheet saved as .xlsx previews correctly, including its row count; bad .xlsx gives the
  friendly "doesn't look like a valid file" error, never a stack trace.

- [x] **M10 · Upload as a dropzone.** *(The file input is the app's most important control and it's a
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

- [x] **M11 · Collapse the rail on submission-detail (phones).** *(GitHub app: secondary sections are
  disclosures.)* In `student/submission-detail.html`, wrap the rail's "Version history" and "Suggested
  reviewers" `.card-section` CONTENT in native `<details class="rail-collapse"><summary>…</summary>…</details>`
  (the existing `.card-head h2` text becomes the summary label; keep heading semantics via CSS).
  "Details" section stays always-open. Additive `initRailCollapse()` in app.js: on load, if
  `min-width:861px` set `open = true` on every `.rail-collapse` (desktop unchanged). CSS: style
  `summary` like `.card-head h2` with a chevron, ≥44px tap height. Done-when: at 390px the rail
  reads as two tappable headings; desktop identical to today.
- [x] **M12 · Dashboard greeting compaction.** Add class `page-head--greeting` to the page-head of
  `student/dashboard.html` + `lecturer/dashboard.html`. CSS ≤640: its `h1` → 1.05rem, margin-bottom
  0.15rem; its `.subtitle` ("N waiting for review" / progress line) becomes the emphasis —
  `color:var(--text); font-weight:600`. Done-when: at 390×760 the first submission row is visible
  without scrolling on both dashboards.
- [x] **M13 · Mobile spacing constants.** One audit pass, ≤640: `.container` gutter exactly 16px;
  `.card` and `.card-stack > .card-section` padding exactly 14px 16px; `.page-head` margin-bottom
  0.75rem; sibling section gap 0.75rem. Fix any page with double-padding (card inside padded card).
  Done-when: spacing feels uniform across dashboard/detail/explore at 390px; no nested-padding wells.

**Phase-M guardrails recap:** never rename §6 hooks; CSRF hidden inputs stay; all assets self-hosted;
dark low-glare; desktop ≥861px must remain pixel-stable except where a spec says otherwise; owner
pushes to git (never push); bump SW VERSION per batch; build green before reporting.

---

## Phase D — Depth & Delight (OPUS · art direction by Fable — execute, don't re-decide)

> **Design thesis: depth through layering and light — never through blur.** The owner asked for
> "morphisms / bento / modern grid" richness. Verdicts, binding: **Bento grid: YES** (fits
> dashboards, pure CSS grid, zero GPU). **Aurora washes: YES, static + subtle** (one radial
> gradient, no animation). **Glassmorphism: FAUX ONLY — `backdrop-filter` is BANNED** in this
> codebase (it crashed low-RAM phones; we removed it in the anti-crash pass — do not reintroduce).
> **Neumorphism: NO as a system** (contrast/a11y trap on dark, dated) — a whisper is allowed inside
> stat tiles only. Palette/typography/tokens stay EXACTLY as-is (Nocturne Laurel). Reduced-motion
> guard applies to anything that moves. Desktop and mobile both get these; keep §6 hooks + CSRF.
> Reference (vocabulary only, NOT drop-in code — that repo/21st.dev serve React, we are Thymeleaf):
> github.com/nextlevelbuilder/ui-ux-pro-max-skill.

- [x] **D1 · Elevation & light system (foundation — do first).** In `base.css` tokens: add
  `--edge-light: rgba(255,255,255,0.045)`. New rule: `.card, .card-stack, .stat-tile, .modal-card
  { box-shadow: inset 0 1px 0 var(--edge-light); }` combined with existing borders (append, don't
  replace existing shadows) — every surface reads "lit from above". Add `.surface-glass` utility:
  `background: color-mix(in srgb, var(--surface) 72%, transparent); border: 1px solid
  rgba(255,255,255,0.06);` — faux glass, NO backdrop-filter. Use it ONLY on: the sticky
  `.review-actions` bar (M4) and `.alert.toast-mode`. Done-when: cards feel subtly dimensional at
  a glance; grep confirms zero `backdrop-filter` in the file.
- [x] **D2 · Bento dashboards.** Restructure the TOP of `student/dashboard.html` and
  `lecturer/dashboard.html` into a bento band above the existing list: CSS grid `.bento`
  (`display:grid; gap:12px; grid-template-columns:repeat(4,1fr)`; ≤640px: `repeat(2,1fr)`).
  Tiles (`.bento-tile` = card styling + D1 edge light): student → [Projects count · 2×1 wide],
  [Approved count], [Pending count], [New submission — primary action tile, `.bento-tile--action`
  with jade gradient wash `linear-gradient(135deg, rgba(46,143,127,.28), transparent 70%)` and the
  "+" glyph]; lecturer → [Waiting for review · 2×1, count in Fraunces 2rem], [Approved this term],
  [Announcements], [Export CSV action tile]. Numbers use `font-variant-numeric: tabular-nums`,
  Fraunces for the big figures, 11px uppercase labels. Wire counts from attributes ALREADY in the
  model where available (submissions list sizes computable via Thymeleaf `#lists`/streams — no new
  Java unless a count is genuinely absent; if absent, add it to the existing controller method, one
  line). Done-when: both dashboards open on a scannable 4-tile band (2-col on phone), stat filters
  and list hooks untouched.
- [x] **D3 · Aurora washes (static).** One shared pattern: `.aurora::before { content:""; position:
  absolute; inset:0; pointer-events:none; background: radial-gradient(560px 300px at 85% -10%,
  rgba(46,143,127,0.16), transparent 60%), radial-gradient(420px 260px at -10% 110%,
  rgba(205,166,96,0.07), transparent 60%); }` (parent gets position:relative + overflow:hidden).
  Apply to exactly three places: `.auth-side` (login brand panel), the explore hero, the `/about`
  hero. Nowhere else — scarcity is the taste. No animation. Done-when: the three heroes have a
  quiet two-tone atmosphere; nothing else changed.
- [x] **D4 · Match score ring.** In the match/recommendation card head (components.html), replace
  the "Possible match NN%" text pill with `.score-ring`: a 34px conic ring —
  `background: conic-gradient(var(--primary) calc(var(--score)*1%), var(--surface-2) 0);
  border-radius:50%;` inner mask via `::after` (26px circle, background var(--surface));
  centered percentage in 10px tabular-nums; keep the accessible text (`aria-label="NN% match"`).
  Set `--score` inline via `th:style`. High scores (≥70) switch the arc to `--gold` (`.score-ring
  .is-strong`). The identical-document 100% case keeps its existing label treatment. Done-when:
  match cards read score-at-a-glance as a ring; screen readers still announce the percentage.
- [ ] **D5 · Empty-state glyphs.** Add ONE reusable set of 5 inline SVG line glyphs (40px, stroke
  `currentColor`, color `--text-subtle`, stroke-width 1.5): folder (projects), compass (explore/
  discover), envelope (inbox), bell (notifications), page (documents). Place above the one-line
  empty-state text via a small Thymeleaf fragment `components :: emptyGlyph(name)`. Done-when:
  every empty state = glyph + one line; no new words added.
- [x] **D6 · Ship check.** Bump `sw.js` VERSION (next after current), build green, verify at 390px
  AND desktop: no horizontal scroll, no `backdrop-filter`, reduced-motion clean, §6 hooks intact.

**Also in this batch (functional, not design):**
- [ ] **D7 · Uploads volume note (OWNER action, Railway UI):** service → Volumes → mount at
  `/app/uploads`. Railway's filesystem is ephemeral: every deploy wipes un-mounted uploads — this is
  the root of "Uploaded file is no longer available". Old files are unrecoverable (rows remain);
  students re-upload a new version. After mounting, files survive deploys.
- [x] **D8 · Graceful missing-file state.** When `/files/{path}` 404s, the review doc panel already
  falls back; ALSO catch it on the student side: in `fragments/components.html` version list, no code
  change needed for the link itself, but change `FileController`'s 404 reason to include guidance:
  "This file predates the storage fix — upload a new version to restore it." Done-when: the dead-file
  experience explains itself.

---

## Phase R — Refine (OPUS · from Fable's review of the 2026-07-15 phone screenshots)

> Verdict from the screenshots: the app-shell works — bottom nav, dashboard, dropzone, queue
> grouping and rail disclosures all read like a real app. What remains is one genuine break (R1)
> and a set of consistency sins. Execute top-down; same guardrails as Phase M (§6 hooks, CSRF,
> no backdrop-filter, reduced-motion, owner pushes, bump SW once at the end, build green).

- [x] **R1 · BROKEN: announcements tables on phones.** Screenshot shows `student/announcements.html`
  assignments table squeezed to one-word-per-line message text, clipped deadline chip, x-scroll —
  M3 only covered admin/. Fix: add `table-stack` to its `.table-wrap`, add class `stack-full` to the
  ASSIGNMENT/message `<td>` (long text must take the full row). Check `lecturer/announcements.html`
  notice-history for the same pattern. Done-when: at 390px the assignment reads title-first,
  full-width message, visible deadline; zero x-scroll.
- [x] **R2 · Queue rows: tap the row, not a button.** Lecturer queue repeats a bordered "Review"
  button on EVERY row (heavy, and approved rows still say "Review"). Make the whole `<tr>` tappable:
  keep the existing link URL, apply it to the row (data-href + tiny additive JS `initRowLinks()` on
  `tr[data-row-href]`, `cursor:pointer`, click → location; keyboard: make the title cell contain the
  real `<a>` for a11y), remove the per-row button, add a `›` chevron via CSS `::after` on the last
  cell. ALSO: show the type chip ONLY for GROUP submissions (exception-labeling — "Individual" on
  every row is noise): wrap the chip in `th:if="${...type == 'GROUP'}"` (adjust to actual model).
  Done-when: rows open the review on tap, one chevron per row, no buttons, GROUP still labeled.
- [x] **R3 · Kill the dashed double-containers + inbox tone.** Global: `.empty-state { border: none;
  background: none; }` (the dashed box inside a card reads as a broken frame — the words suffice).
  In `student/inbox.html`: shorten the three empty lines to "No requests yet." / "No active
  collaborations." / "Nothing sent yet — find matches in Discover." AND the page subtitle dies.
  Normalize the "Find new collaborators" button from accent-green to standard `.btn` jade (accent
  green is reserved for Approve actions). Done-when: inbox is one calm screen, no dashed frames
  anywhere in the app.
- [x] **R4 · Details rows stack on phones.** The `.info-row` label/value pair wraps right-aligned
  values into a ragged 3-line column ("Department of Computer Science — BSc…"). ≤640px:
  `.info-row { flex-direction: column; align-items: flex-start; gap: 2px; }` (+ value text-align
  left). Done-when: Details reads as label-over-value blocks, no ragged right edge.
- [x] **R5 · "Re-run AI analysis" placement.** It floats right with a void to its left (screenshot 1).
  ≤640px make it full-width `btn-secondary btn-sm` (`.ai-rerun-row { justify-content: stretch }` or
  equivalent); desktop unchanged. Done-when: no floating lone button on mobile.
- [x] **R6 · Explore search composition.** Placeholder truncates ("e.g. traffic p") beside an
  oversized Search button. ≤640px: input takes full width (`flex: 1 1 100%`), button becomes
  compact (auto width, same row if it fits or second line full-width); shorten placeholder to
  "Search projects…". Also covered by R3: the dashed "Search the archive." box loses its border.
  Done-when: search bar looks composed at 390px, placeholder legible.
- [x] **R7 · Word diet, round two.** (a) Lecturer "Workload" card: DELETE the "Visible in filter"
  and "Units shown" rows (internal metrics — keep Pending reviews / Completed decisions).
  (b) Announcements form: "Post notice" button full-width on ≤640; trim the two helper lines to
  one each. (c) Student announcements page subtitle ("Deadlines and instructions from your unit
  lecturers, newest first.") → delete; the h1 says it. Done-when: nothing on these screens
  explains what's already visible.
- [x] **R8 · Mystery checkbox glyphs on dashboard rows.** Student project rows show an empty
  square outline before "Applied Machine Learning" that reads as a broken checkbox. Find it in
  `student/dashboard.html` (likely a decorative unit icon) — replace with the 16px folder glyph
  from the bottom nav, or delete it. Done-when: no ambiguous empty squares.
- [x] **R9 · (Optional) date-only timestamps in the queue.** "11 Jul 2026, 23:50" → drop the time
  on the lecturer queue rows (template `#temporals.format(..., 'dd MMM yyyy')`) — the minute a
  student uploaded is never the deciding datum. Skip if any test depends on the format.
- [x] **R10 · Ship check.** Bump `sw.js` VERSION, build green, then verify at 390px: announcements,
  queue, inbox, explore, submission detail. Desktop must be pixel-stable except where specced.

---

# ═══ VERSION 2 — "THE REGISTRY" REBRAND (art direction locked by Fable) ═══

> **Why:** the current look reads competent-generic ("AI-built smell"). V2 is a full identity
> rebrand — structure and personality, not a palette swap — for university students + lecturers,
> mobile AND desktop. **The direction below is DECIDED. Opus executes; zero re-deciding.**
>
> **The concept — "The Registry":** a university is paper — ledgers, stamps, index cards, docket
> numbers, notice boards. UniSubmit V2 looks like a beautifully-run registrar's archive at night:
> warm ink surfaces, parchment text, typewriter metadata, statuses that are literal STAMPS.
> Nothing floats, nothing glows, nothing is a smooth teal pill. Every surface has an honest edge.
>
> **Anti-slop rules (binding):** no teal/purple/blue-gradient anywhere · no glassy translucency ·
> no backdrop-filter (still banned) · no rounded-pill buttons (radius 2px everywhere; the ONLY
> circles are avatars and score rings) · no decorative gradients except the two paper-grain
> radial washes specced in V2.3 · no emoji in UI · metadata is ALWAYS mono · if an element could
> appear in a generic dashboard template, redesign it.

## V2.0 — Safety net + reality prep (OPUS · DO FIRST, before any wipe or restyle)
- [x] **V2.0a Preserve v1 + git strategy (BINDING for all V2 work).** Created tag `v1.0` and backup branch `v1-backup` (pushed).
  **Branch & Worktree flow:**
  - **V2.0 (safety/backend) lands on `main` directly** — work done in primary folder, pushed to main -> Railway deploys.
  - **V2.1–V2.3 (visual rebrand) happen in worktree `unisubmit-v2`** linked to branch `v2-registry`.
    Worktree setup: `git worktree add ../unisubmit-v2 v2-registry` (completed).
    All V2.1–V2.3 changes are committed/pushed from the `unisubmit-v2` directory.
  - **Merge gate:** after the design-review checkpoint passes, merge `v2-registry` into `main` and deploy.
  - V2.4+ continue on main post-merge.
- [ ] **V2.0b GATE RichTestDataSeeder (verified ungated at config/RichTestDataSeeder.java:23).**
  Add `@ConditionalOnProperty(name = "unisubmit.demo.seed-rich-data", havingValue = "true")`;
  property `true` ONLY in application-local.yml. ALSO strip the demo `lecturer`/`student` accounts
  from `UnisubmitApplication.seedData` behind the same flag (keep `admin` + lookup tags — a fresh
  prod boot still needs an admin). WITHOUT THIS, WIPING THE DB IS POINTLESS (reseeds on boot).
- [ ] **V2.0c Forced password change.** Real users are coming. Add `must_change_password boolean
  default false` to users (Hibernate adds column); set TRUE on: CSV-imported accounts and the
  seeded admin. A `HandlerInterceptor` redirects flagged users to `/account/password` (new page,
  minimal) until changed. Done-when: fresh admin login forces a change; CSV students likewise.
- [ ] **V2.0d Lecturer CSV import** (owner asked "do teachers need a csv" — yes, adoption).
  Second importer alongside students: columns `name,email,staffId,departmentCode`. Same
  preview→apply→credentials flow, same validateAndAdd-style shared rules, generated passwords +
  must_change_password. Tab or second card on /admin/import.
- [ ] **V2.0e Wipe day runbook (owner action, AFTER 0b deploys):** `pg_dump` backup via Supabase →
  delete demo rows (or full wipe + let migrations/seeder rebuild schema + admin) → mount the
  Railway Volume at `/app/uploads` if STILL unmounted (root cause of both "file no longer
  available" AND "Could not load the document preview") → change admin password on first login.

## V2.1 — Registry tokens + type (OPUS · the foundation; every page inherits)
- [ ] **V2.1a Fonts:** replace Inter/Fraunces with the **IBM Plex trio** (free, self-host woff2
  latin subsets to `static/fonts/`, same download method as F2.1): **Plex Serif SemiBold** =
  display/h1-h2 · **Plex Sans** 400/500/600 = UI · **Plex Mono** 400/500 = ALL metadata (IDs,
  codes, dates, stamps, table headers, figure numbers). Update `--font-display/--font-sans/
  --font-mono`, remove old font files + @font-face.
- [ ] **V2.1b Palette (full token swap in base.css :root — no teal survives):**
  canvas `#141109` warm ink-black · surface `#1C1812` · raised `#252017` · hairline `#3A3225` /
  strong `#4E4433` · text `#EDE4CE` parchment · muted `#A69A7E` · subtle `#7A7059` ·
  **accent = oxblood `#B5453A`** (links, active nav, focus ring, primary-button border) ·
  primary button = parchment text on `#2E2617` w/ 1px oxblood border (quiet, printed) ·
  approved-stamp green `#5A9367` · changes-stamp red `#C0563F` · pending-stamp `#B08D3F` brass ·
  info `#7D8CA6` slate. Remap EVERY old token (--primary, --brand, --gold, tints, badges) —
  grep for leftover hex values; zero `#5fbfab`/`#2e8f7f`/`#cda660` may remain.
- [ ] **V2.1c Geometry:** `--radius: 2px` and `--radius-lg: 3px` (sharp, printed); pill radius
  reserved ONLY for avatars + score rings. Buttons/inputs/cards/badges all inherit. Focus ring
  → oxblood. Section separations become RULES: `.card-head` gets a 2px bottom rule (--border-strong)
  under the title like a ledger heading. Update manifest `theme_color`/`background_color` to
  `#141109`, regenerate favicon + PWA icons from the brandMark recolored (mark keeps its geometry,
  tile becomes ink `#1C1812`, U parchment, node oxblood) — same GDI+ script approach. SW bump.

## V2.2 — Registry components (OPUS)
- [ ] **V2.2a STAMPS.** Replace all status badges (`statusBadge` fragment + .badge styles):
  mono uppercase, letter-spacing .08em, 1.5px solid border in the status color, transparent bg,
  radius 2px, padding 2px 8px. On DETAIL pages only (submission/project/review), the stamp gets
  `transform: rotate(-1.2deg)` + slightly heavier border — a real stamp; in tables/rows it stays
  straight. APPROVED green / CHANGES REQUESTED red / UNDER REVIEW brass / SUBMITTED parchment-muted.
- [ ] **V2.2b Docket numbers.** Every submission detail + review page shows a mono identifier
  chip near the title: `№ {id}` (Plex Mono, muted). Registrar authenticity, zero backend change.
- [ ] **V2.2c Index-card rows.** List rows (dashboard, explore results, matches): remove card-in-
  card look → full-bleed rows separated by hairlines, title in Plex Serif 1.05rem, meta line in
  mono 0.72rem (`DCS410 · 12 Jul · v3`), stamp right-aligned. Hover/active = surface-raised bg.
- [ ] **V2.2d Buttons & forms.** Primary = parchment-on-ink w/ oxblood border (V2.1b); secondary =
  hairline border transparent bg; destructive = red border. Inputs: flat `#1C1812`, 1px hairline,
  2px radius, oxblood focus border — labels stay above, mono 0.7rem uppercase. Kill every remaining
  `.btn-accent` teal-green.
- [ ] **V2.2e Score ring recolor** (keep D4 structure): arc oxblood, ≥70% brass; center bg
  follows new surface tokens.

## V2.3 — Public face + shell (OPUS)
- [ ] **V2.3a Login/register:** single centered printed card on the ink canvas with ONE static
  paper-grain wash (`radial-gradient(720px 420px at 78% -12%, rgba(237,228,206,0.045), transparent
  62%)` — replaces aurora, delete .aurora). Wordmark in Plex Serif, "Sign in" only — zero pitch
  copy on login (the /about link carries it). Register equally bare.
- [ ] **V2.3b /about rewrite in Registry voice:** terse, registrar-factual ("Every project,
  archived. Every match, explained."), stamps as visual motif, one screenshot placeholder slot
  for a REAL student project (owner supplies after pilot).
- [ ] **V2.3c Shell:** topbar bg = canvas w/ 2px bottom rule; bottom-nav same treatment (active =
  oxblood text + 2px top rule on the active tab, no tint pill); page-head h1 in Plex Serif w/
  its ledger rule. Toasts/modals inherit tokens automatically — verify only.
- [ ] **V2.3d Voice pass:** registrar-terse microcopy sweep — buttons are verbs ("Submit", "Approve",
  "Export"), dates "12 Jul", no exclamation marks, no "successfully" (a thing done is done:
  "Submission recorded.").
- [ ] **V2.3e COMPLETE surface sweep (the "whole-app redesign", not just inherited tokens).**
  Walk EVERY surface at 1280px and 390px and bring it to Registry deliberately: student dashboard ·
  submission detail · new submission · lecturer dashboard · review workspace · announcements (both
  roles) · explore/discover/search · inbox · groups · notifications · all 10 admin pages · error
  pages · offline page. Per page: (1) components adopt V2.2 grammar (stamps, index-card rows,
  ledger rules, mono meta); (2) layout uses the editorial grid — content column + quiet margin,
  asymmetry over centered-everything; (3) anything still looking like a generic dashboard card gets
  redesigned, not recolored. Done-when: no page passes as "template default".
- [ ] **V2.3f Simplicity audit (the "no unnecessary explanation" pass — owner's core demand).**
  Rule: a sentence survives ONLY if removing it could cause a wrong action. Sweep every template:
  subtitles that restate the h1 → delete; hints that restate the label → delete; empty states >
  1 sentence → cut to 1; the login card = wordmark, two fields, one button, two links, NOTHING else.
  Done-when: grep for `field-hint|subtitle` returns only entries that pass the rule, documented in
  the log line.

**⛔ CHECKPOINT — Fable design review (do NOT proceed past here on the v2-registry branch):**
after V2.3f builds green, the owner sends one desktop + one phone screenshot set (login, a
dashboard, a submission detail with stamps) to a Fable session for art-direction sign-off. Only
after sign-off → merge `v2-registry` → `main` (per V2.0a) and continue with V2.4.

## V2.4 — Cold start / first five minutes (OPUS · the "never met reality" fix)
- [ ] **V2.4a Admin day-zero:** fresh-DB admin dashboard leads with a numbered 3-step card
  (mono numerals): 1 Import structure (link /admin/import) · 2 Import people · 3 Students submit.
  Shows only while faculties==0 or users<=1.
- [ ] **V2.4b Student/lecturer first-run empty states:** dashboard empty = ONE line + primary
  action ("No projects yet." + [Submit your first]); lecturer = "No submissions yet — your
  students will appear here." Each empty state exactly one sentence, Registry voice.
- [ ] **V2.4c Pilot feedback hook:** footer link "Report a problem" → mailto:mbuyabrian290@gmail.com
  with subject prefilled "UniSubmit feedback" — UAT evidence channel for the assessment.

## V2.5 — Evidence pack (OPUS · assessment-critical, from the external review)
- [ ] **V2.5a Security test matrix:** `@SpringBootTest` + MockMvc: student→/lecturer/** = 403,
  lecturer→/admin/** = 403, anon→/student/** = 302 login, CSRF-less POST = 403. One file,
  ~8 tests, run in CI before package.
- [ ] **V2.5b Controller smoke tests:** login page 200, /about 200, authenticated dashboard 200
  per role (seed via test profile).
- [ ] **V2.5c Docs:** `docs/architecture.md` w/ one mermaid component diagram + `docs/user-guide.md`
  (1 page per role, screenshots after pilot); link both from README.
- [ ] **V2.5d** JaCoCo plugin + coverage badge in README (number for the report chapter).

**V2 guardrails:** §6 JS hooks + CSRF contract STILL BINDING (restyle, never rename) · templates
change classes only where a spec says · SW bump per batch · build green per phase · owner pushes ·
desktop + mobile verified at 1280px and 390px per phase (owner does authed screens).

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
- 2026-07-14 Opus: Phase M COMPLETE (M3 stacked admin tables · M4 sticky review bar · M5 mobile toasts · M7 real page titles · M8 native share · M9 .xlsx import via Apache POI, shared validation pipeline, dispatch-by-extension · M10 dropzone upload · M11 rail disclosures · M12 dashboard greeting compaction · M13 spacing rhythm). Build green, SW v10. M1/M2/M6 were already done by Fable. ALL PHASES DONE.
- 2026-07-15 Opus: Phase R COMPLETE. R1 announcements table stacks (student) + stack-full on message. R2 lecturer queue rows fully tappable (initRowLinks, chevron, per-row Review button removed, GROUP-only chip). R3 .empty-state dashed frame removed globally + inbox copy trimmed + green button normalized. R4 info-row stacks on phones. R5 Re-run AI full-width on mobile. R6 explore search composes + placeholder shortened. R7 workload "Visible in filter"/"Units shown" removed, subtitles deleted, Post-notice full-width. R8 mystery square glyph -> folder. R9 queue timestamps date-only. Build green, SW v11. NOTE: 390px visual verify is owner-side (cannot self-login). Phase D (bento/rings/aurora) + D7 Railway volume remain.
- 2026-07-15 Opus: Phase D — D1 elevation (--edge-light inset top-light on cards + faux-glass review bar, NO backdrop-filter), D2 bento stat tiles (student At-a-glance + lecturer Workload, in-place in rail — deviation: kept in rail not moved to top-band, lower risk), D3 static aurora on login/explore/about heroes, D4 conic score RING replaces match pill (aria-label preserved, gold arc >=70- 2026-07-15 Opus: Phase D done — D1 elevation (edge-light inset highlight + faux-glass review bar, NO backdrop-filter), D2 bento tiles (student + lecturer, in-place in rail; deviation: not moved to top band, lower risk), D3 static aurora on login/explore/about, D4 conic score RING replaces match pill (aria-label kept, gold arc at >=70), D8 friendlier missing-file text. Build green, SW v12. DEFERRED D5 empty-state glyphs (polish; empties already clean post-R3). D7 owner-only (Railway Volume). Could NOT self-verify visually (port reserved + no self-login) — check dashboards/components on device.
