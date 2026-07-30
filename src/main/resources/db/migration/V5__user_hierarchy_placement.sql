-- ============================================================================
-- V5__user_hierarchy_placement.sql
-- Extends users table with programme placement and year of study.
-- Additive / idempotent (safe on existing DB).
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS programme_id  BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS year_of_study INTEGER;

-- Soft-reference only (no hard FK) — keeps soft-deleted users intact
-- even if their programme is later removed.
