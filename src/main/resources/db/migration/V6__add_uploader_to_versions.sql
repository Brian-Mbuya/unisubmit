-- ============================================================================
-- V6__add_uploader_to_versions.sql
-- Add uploaded_by_id to tracking who uploaded each submission version.
-- ============================================================================

ALTER TABLE submission_versions 
    ADD COLUMN IF NOT EXISTS uploaded_by_id BIGINT 
    REFERENCES users(id);
