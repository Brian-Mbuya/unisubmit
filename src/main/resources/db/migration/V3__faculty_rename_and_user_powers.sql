-- ============================================================================
-- V3__faculty_rename_and_user_powers.sql
-- 1. Rename School -> Faculty (table + FK column), preserving existing data.
-- 2. Add faculty/department placement + soft-delete + suspension to users.
-- 3. Allow the SYSTEM_NOTICE notification type.
-- Additive / idempotent: safe to run against an existing schema.
-- (Schema is currently managed by Hibernate ddl-auto; this migration is the
--  source of truth when Flyway is re-enabled.)
-- ============================================================================

-- ── 1. Rename schools -> faculties (only if not already renamed) ─────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'schools')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = 'faculties') THEN
        ALTER TABLE schools RENAME TO faculties;
    END IF;
END $$;

-- ── 2. Rename departments.school_id -> faculty_id (do not recreate table) ────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'departments'
                 AND column_name = 'school_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'departments'
                         AND column_name = 'faculty_id') THEN
        ALTER TABLE departments RENAME COLUMN school_id TO faculty_id;
    END IF;
END $$;

-- ── 3. User: academic placement, soft-delete, suspension ─────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS faculty_id       BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS department_id    BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_reason TEXT;

-- ── 4. Permit SYSTEM_NOTICE in app_notifications.type ────────────────────────
-- V2 created chk_notif_type with three values; widen it to include SYSTEM_NOTICE.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'app_notifications') THEN
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_notif_type') THEN
            ALTER TABLE app_notifications DROP CONSTRAINT chk_notif_type;
        END IF;
        ALTER TABLE app_notifications
            ADD CONSTRAINT chk_notif_type
            CHECK (type IN ('NEW_FEEDBACK', 'STATUS_CHANGE', 'DEADLINE', 'SYSTEM_NOTICE'));
    END IF;
END $$;
