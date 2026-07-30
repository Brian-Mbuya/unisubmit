-- ============================================================================
-- V2__academic_foundation.sql
-- Phase 1 — Academic Foundation
-- Additive only: no DROP TABLE, no DROP COLUMN, no data loss.
-- Consistent with railway-collaboration.sql style (plain SQL, no Flyway DSL).
-- ============================================================================

-- ── 1. School ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS schools (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50)  NOT NULL,
    CONSTRAINT ux_schools_name UNIQUE (name),
    CONSTRAINT ux_schools_code UNIQUE (code)
);

-- ── 2. Department ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS departments (
    id        BIGSERIAL    PRIMARY KEY,
    school_id BIGINT       NOT NULL,
    name      VARCHAR(255) NOT NULL,
    code      VARCHAR(50)  NOT NULL,
    CONSTRAINT fk_departments_school
        FOREIGN KEY (school_id) REFERENCES schools(id)
);

CREATE INDEX IF NOT EXISTS ix_departments_school
    ON departments (school_id);

-- ── 3. Programme ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS programmes (
    id            BIGSERIAL    PRIMARY KEY,
    department_id BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    CONSTRAINT fk_programmes_department
        FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX IF NOT EXISTS ix_programmes_department
    ON programmes (department_id);

-- ── 4. Academic Year ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS academic_years (
    id         BIGSERIAL    PRIMARY KEY,
    label      VARCHAR(20)  NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    CONSTRAINT ux_academic_years_label UNIQUE (label)
);

-- ── 5. Semester ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS semesters (
    id               BIGSERIAL    PRIMARY KEY,
    academic_year_id BIGINT       NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    CONSTRAINT fk_semesters_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

CREATE INDEX IF NOT EXISTS ix_semesters_academic_year
    ON semesters (academic_year_id);

-- ── 6. Unit ↔ Semester join ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS unit_semesters (
    semester_id BIGINT NOT NULL,
    unit_id     BIGINT NOT NULL,
    PRIMARY KEY (semester_id, unit_id),
    CONSTRAINT fk_unit_semesters_semester
        FOREIGN KEY (semester_id) REFERENCES semesters(id),
    CONSTRAINT fk_unit_semesters_unit
        FOREIGN KEY (unit_id) REFERENCES units(id)
);

-- ── 7. Link existing units to departments (nullable FK) ─────────────────────
ALTER TABLE units
    ADD COLUMN IF NOT EXISTS department_id BIGINT
    REFERENCES departments(id);

-- ── 8. Project Group ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_groups (
    id        BIGSERIAL    PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    leader_id BIGINT       NOT NULL,
    CONSTRAINT fk_project_groups_leader
        FOREIGN KEY (leader_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS ix_project_groups_leader
    ON project_groups (leader_id);

-- ── 9. Project Group ↔ Member join ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_group_members (
    group_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_pgm_group
        FOREIGN KEY (group_id) REFERENCES project_groups(id),
    CONSTRAINT fk_pgm_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ── 10. Link submissions to project groups (nullable) ────────────────────────
ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS project_group_id BIGINT
    REFERENCES project_groups(id);

-- ── 11. Submission ↔ Supervisor (lecturer) many-to-many ──────────────────────
CREATE TABLE IF NOT EXISTS submission_supervisors (
    submission_id BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    PRIMARY KEY (submission_id, user_id),
    CONSTRAINT fk_ss_submission
        FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_ss_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ── 12. In-App Notifications ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_notifications (
    id                    BIGSERIAL    PRIMARY KEY,
    recipient_id          BIGINT       NOT NULL,
    type                  VARCHAR(30)  NOT NULL,
    message               VARCHAR(500) NOT NULL,
    related_submission_id BIGINT,
    read                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_recipient
        FOREIGN KEY (recipient_id) REFERENCES users(id),
    CONSTRAINT chk_notif_type
        CHECK (type IN ('NEW_FEEDBACK', 'STATUS_CHANGE', 'DEADLINE'))
);

CREATE INDEX IF NOT EXISTS ix_app_notifications_recipient_unread
    ON app_notifications (recipient_id, read, created_at DESC);

-- ── 13. Extend submission_status enum with lifecycle values ──────────────────
-- NOTE: In this application Submission.status is mapped with
-- @Enumerated(EnumType.STRING), so it is stored as VARCHAR (with a CHECK
-- constraint), NOT as a native PostgreSQL ENUM type. The new lifecycle values
-- (PROPOSAL, FINAL, ARCHIVED, UNDER_REVIEW) therefore need NO schema change —
-- they are just strings the application writes into the varchar column.
--
-- The blocks below only act if a native enum type named 'submission_status'
-- actually exists (typtype = 'e'). Without this "type exists AND is an enum"
-- guard, the migration would fail with "type submission_status does not exist"
-- on a Hibernate-managed schema.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'submission_status' AND typtype = 'e'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'PROPOSAL'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'submission_status')
    ) THEN
        ALTER TYPE submission_status ADD VALUE 'PROPOSAL';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'submission_status' AND typtype = 'e'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'FINAL'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'submission_status')
    ) THEN
        ALTER TYPE submission_status ADD VALUE 'FINAL';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'submission_status' AND typtype = 'e'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'ARCHIVED'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'submission_status')
    ) THEN
        ALTER TYPE submission_status ADD VALUE 'ARCHIVED';
    END IF;
END $$;
