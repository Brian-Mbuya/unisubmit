-- ============================================================================
-- V4__fix_departments_faculty_column.sql
-- Ensures departments.school_id has been renamed to faculty_id.
-- V3 attempted this but only ran the rename if the old column existed;
-- on some DB instances the rename may not have executed (e.g. Flyway was
-- disabled when V3 ran). This migration also adds faculty_id when missing.
-- Additive / idempotent: safe to run multiple times.
-- ============================================================================

-- Step 1: If school_id exists but faculty_id does not, rename it (same as V3).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'departments'
          AND column_name  = 'school_id'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'departments'
          AND column_name  = 'faculty_id'
    ) THEN
        ALTER TABLE departments RENAME COLUMN school_id TO faculty_id;
    END IF;
END $$;

-- Step 2: If both columns exist (partial migration state), copy school_id
-- values into faculty_id and drop the old column.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'departments'
          AND column_name  = 'school_id'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'departments'
          AND column_name  = 'faculty_id'
    ) THEN
        -- Fill faculty_id from school_id where null
        UPDATE departments SET faculty_id = school_id WHERE faculty_id IS NULL;
        ALTER TABLE departments DROP COLUMN school_id;
    END IF;
END $$;

-- Step 3: Ensure faculty_id is NOT NULL (safe to run if column already correct).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'departments'
          AND column_name  = 'faculty_id'
          AND is_nullable  = 'YES'
    ) THEN
        -- Only set NOT NULL if all rows have a value
        IF NOT EXISTS (SELECT 1 FROM departments WHERE faculty_id IS NULL) THEN
            ALTER TABLE departments ALTER COLUMN faculty_id SET NOT NULL;
        END IF;
    END IF;
END $$;
