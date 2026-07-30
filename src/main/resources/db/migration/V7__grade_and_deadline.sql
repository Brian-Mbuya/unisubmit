-- V7: Add grade column to feedbacks table
-- Safe: only adds if the column does not already exist
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS grade INTEGER;

-- V8: Add submission deadline to units table
ALTER TABLE units ADD COLUMN IF NOT EXISTS submission_deadline TIMESTAMP;

-- Comments:
-- grade: nullable 0-100 numeric score attached to lecturer feedback.
-- submission_deadline: per-unit deadline shown on student dashboard as countdown/OVERDUE badge.
