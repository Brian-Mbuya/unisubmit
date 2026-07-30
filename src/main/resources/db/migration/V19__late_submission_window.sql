-- V19: Late submission window support
-- Allows lecturers to re-open submissions after deadline passes,
-- with late submissions clearly labelled for both parties.

ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS late_window_open BOOLEAN DEFAULT FALSE;

ALTER TABLE submission_versions
    ADD COLUMN IF NOT EXISTS is_late BOOLEAN DEFAULT FALSE;
