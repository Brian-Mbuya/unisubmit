# Phase 1 — Academic Foundation Implementation Plan

## Background

UniSubmit currently has a flat `Unit` entity (with inline `departmentName` and `courseName` fields) sitting directly below `Submission`. This plan lifts the full university hierarchy into first-class entities, adds group project support, a project lifecycle, supervisor many-to-many, a notifications table, and updates the relevant UI pages — all additively, without breaking existing data.

## Open Questions

> [!IMPORTANT]
> **Q1 — Flyway vs JPA DDL?** `application.yml` currently uses `hibernate.ddl-auto: update` (JPA auto-schema). The request asks for Flyway migration files consistent with `railway-collaboration.sql`. Should Flyway be enabled and `ddl-auto` set to `validate`/`none`, or do you only want SQL files for manual/railway deployment while keeping JPA `update` locally?
> **Assumed answer**: Generate standalone `.sql` files (matching `railway-collaboration.sql` style) for Railway deployment. Keep JPA `ddl-auto: update` locally so no Flyway dependency change is needed — this lets the app boot immediately without a running DB migration.

> [!IMPORTANT]
> **Q2 — Existing `Unit.departmentName` / `Unit.courseName` columns?** After the migration, `Unit` will get a FK to `Department` (which belongs to `School`). The two inline string fields become redundant. Should they be **kept** (safe, backward-compatible) or **dropped** (clean but destructive)?
> **Assumed answer**: Keep both columns for now (additive). The new FK is nullable initially so existing rows aren't broken. A Phase 2 migration can back-fill and drop them.

> [!IMPORTANT]
> **Q3 — `SubmissionStatus` lifecycle conflict?** The existing enum has `DRAFT / SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED`. The new lifecycle is `PROPOSAL → UNDER_REVIEW → FINAL → ARCHIVED`. These overlap (`UNDER_REVIEW` exists in both). Should the new values **extend** the existing enum (add `PROPOSAL`, `FINAL`, `ARCHIVED` alongside the existing values), or **replace** the old values?
> **Assumed answer**: Extend — add `PROPOSAL`, `FINAL`, `ARCHIVED` to the existing enum without removing current values. Dashboard filter buttons will be updated to show the new states.

## Proposed Changes

---

### 1. Domain — New Entities

#### [NEW] `School.java`
```java
@Entity @Table(name = "schools")
// id, name (unique), code
```

#### [NEW] `Department.java`
```java
@Entity @Table(name = "departments")
// id, name, code, @ManyToOne School
```

#### [NEW] `Programme.java`
```java
@Entity @Table(name = "programmes")
// id, name, code, @ManyToOne Department
```

#### [NEW] `AcademicYear.java`
```java
@Entity @Table(name = "academic_years")
// id, label (e.g. "2024/25"), startDate, endDate
```

#### [NEW] `Semester.java`
```java
@Entity @Table(name = "semesters")
// id, name (SEM_1/SEM_2/SEM_3), @ManyToOne AcademicYear
// @ManyToMany Unit (units offered this semester) via unit_semesters join table
```

#### [NEW] `ProjectGroup.java`
```java
@Entity @Table(name = "project_groups")
// id, name, @ManyToOne(leader) User, @ManyToMany(members) User
```

#### [NEW] `AppNotification.java`
```java
@Entity @Table(name = "app_notifications")
// id, @ManyToOne recipient User, type (enum), message, relatedSubmissionId, read, createdAt
```

#### [NEW] `NotificationType.java`
```java
public enum NotificationType { NEW_FEEDBACK, STATUS_CHANGE, DEADLINE }
```

#### [MODIFY] [Unit.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/domain/Unit.java)
- Add `@ManyToOne(optional=true) Department department` FK (nullable so existing rows survive).
- Keep existing `departmentName`, `courseName`, `unitName` columns.

#### [MODIFY] [Submission.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/domain/Submission.java)
- Add `@ManyToOne(optional=true) ProjectGroup projectGroup`
- Add `@ManyToMany` supervisors (`Set<User>`) via `submission_supervisors` join table
  - Validation: supervisor must be a LECTURER in the unit's department (service layer guard)

#### [MODIFY] [SubmissionStatus.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/domain/SubmissionStatus.java)
- Append `PROPOSAL`, `FINAL`, `ARCHIVED` to existing enum.

---

### 2. Repository Layer

#### [NEW] `SchoolRepository.java`
#### [NEW] `DepartmentRepository.java`
#### [NEW] `ProgrammeRepository.java`
#### [NEW] `AcademicYearRepository.java`
#### [NEW] `SemesterRepository.java`
#### [NEW] `ProjectGroupRepository.java`
#### [NEW] `AppNotificationRepository.java`
- `findByRecipientAndReadFalse(User)` — for unread count

---

### 3. Service Layer

#### [NEW] `AcademicHierarchyService.java`
- `findAllSchools()`, `findDepartmentsBySchool(Long)`, `findUnitsByDepartment(Long)` — used by the cascading selector AJAX endpoints.

#### [NEW] `ProjectGroupService.java`
- `createGroup(leader, name)` — only leader/admin can add/remove members.
- `addMember(requestingUser, groupId, memberId)` — `@PreAuthorize("...")` guard.
- `removeMember(requestingUser, groupId, memberId)` — same guard.

#### [NEW] `NotificationService.java`
- `createNotification(recipient, type, message, submissionId)`
- `getUnreadCount(user)` — called from `GlobalModelAttributes`
- `markAllRead(user)`

#### [MODIFY] [SubmissionService.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/service/SubmissionService.java)
- `addSupervisor(submissionId, lecturerId)` — validate lecturer belongs to unit's department before adding.
- Trigger `NotificationService.createNotification(...)` on feedback + status changes.

#### [MODIFY] [UnitService.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/service/UnitService.java)
- `createUnit(departmentId, unitName)` — new overload that sets the department FK.

---

### 4. Controller Layer

#### [NEW] `AcademicApiController.java` (`/api/academic`)
- `GET /api/academic/schools` → `List<School>`
- `GET /api/academic/departments?schoolId=` → `List<Department>`
- `GET /api/academic/units?departmentId=` → `List<Unit>`
(Returns JSON for cascading selector JavaScript)

#### [NEW] `ProjectGroupController.java` (`/groups`)
- `POST /groups` — create group
- `POST /groups/{id}/members` — add member (`@PreAuthorize`)
- `DELETE /groups/{id}/members/{userId}` — remove member (`@PreAuthorize`)

#### [NEW] `NotificationController.java` (`/notifications`)
- `GET /notifications` — list all notifications for current user
- `POST /notifications/mark-read` — mark all read

#### [MODIFY] [GlobalModelAttributes.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/controller/GlobalModelAttributes.java)
- Add `@ModelAttribute("unreadNotificationCount")` backed by `NotificationService.getUnreadCount(user)`.

#### [MODIFY] [StudentController.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/controller/StudentController.java)
- `newSubmissionForm` — inject all `schools` into model (departments/units loaded via AJAX).

#### [MODIFY] [AdminController.java](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/java/com/unisubmit/controller/AdminController.java)
- Expose new POST endpoints for creating Schools, Departments, Programmes.

---

### 5. UI Templates

#### [MODIFY] [layout.html](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/resources/templates/layout.html)
- Add **notification bell** in `user-nav`, showing `unreadNotificationCount` badge (similar to existing Inbox badge).
- Bell links to `/notifications`.

#### [MODIFY] [new-submission.html](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/resources/templates/student/new-submission.html)
- Replace single `<select id="unitId">` with a **three-step cascading selector**:
  1. School `<select id="schoolId">`
  2. Department `<select id="departmentId">` (disabled until school chosen)
  3. Unit `<select id="unitId">` (disabled until dept chosen)
- Add JavaScript that hits `/api/academic/departments?schoolId=` and `/api/academic/units?departmentId=` to populate each level.

#### [MODIFY] [submission-detail.html](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/resources/templates/student/submission-detail.html)
- Add **breadcrumb** bar above the page header:
  `School → Department → Programme → Unit`
  Built from `submission.unit.department.school.name`, etc.

#### [MODIFY] [dashboard.html](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/resources/templates/student/dashboard.html)
- Status pill colors updated to include new states:
  - `PROPOSAL` → grey
  - `UNDER_REVIEW` → amber (existing)
  - `FINAL` → green
  - `ARCHIVED` → slate

#### [MODIFY] [components.html](file:///c:/Users/mbuya/OneDrive/Desktop/unisubmit-main/src/main/resources/templates/fragments/components.html)
- Update `statusBadge` fragment CSS class mapping to cover new enum values.

#### [NEW] `notifications.html` (under `templates/`)
- Lists all user notifications with read/unread styling and a "Mark all read" button.

---

### 6. SQL Migration File

#### [NEW] `V2__academic_foundation.sql`
Additive DDL only — no DROP or ALTER TABLE DROP COLUMN statements:
- `CREATE TABLE schools`
- `CREATE TABLE departments` (FK → schools)
- `CREATE TABLE programmes` (FK → departments)
- `CREATE TABLE academic_years`
- `CREATE TABLE semesters` (FK → academic_years)
- `CREATE TABLE unit_semesters` (join: unit ↔ semester)
- `ALTER TABLE units ADD COLUMN department_id BIGINT REFERENCES departments(id)` (nullable)
- `CREATE TABLE project_groups`
- `CREATE TABLE project_group_members` (join: group ↔ user)
- `ALTER TABLE submissions ADD COLUMN project_group_id BIGINT REFERENCES project_groups(id)` (nullable)
- `CREATE TABLE submission_supervisors` (join: submission ↔ user/lecturer)
- `CREATE TABLE app_notifications`
- `ALTER TYPE submission_status ADD VALUE IF NOT EXISTS 'PROPOSAL'` etc. (PostgreSQL enum extension)

> [!WARNING]
> PostgreSQL `ALTER TYPE ... ADD VALUE` cannot run inside a transaction block. The SQL file will use `COMMIT; ALTER TYPE ...; BEGIN;` pattern (or the values will be added before the table DDL where transaction isolation isn't an issue). Railway typically runs each migration file in autocommit mode, so this should be fine.

---

## Verification Plan

### Automated Tests
```bash
./mvnw compile          # Must produce zero errors after entity/enum changes
./mvnw test             # Existing tests must still pass
```

### Manual Verification
1. Start the app with a local PostgreSQL — JPA `ddl-auto: update` will auto-create the new tables.
2. Admin creates a School → Department → Unit chain.
3. Student opens "New submission" — cascading dropdowns correctly filter to only units in the chosen school/department.
4. Student submits — submission detail page shows the full breadcrumb.
5. Lecturer posts feedback — a notification appears in the student's bell (unread count > 0).
6. Student opens `/notifications` — can mark all read; count returns to 0.
7. Status pills for `PROPOSAL`, `FINAL`, `ARCHIVED` render in correct colors on the dashboard.
8. Attempt to add a supervisor from a different department — system rejects with a meaningful error.
9. Non-leader student attempts to add a group member — Spring Security returns 403.
