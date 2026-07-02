-- Phase 5 — Recommendation Engine expansion
-- Per-signal score breakdown ("why this match") + structured tag overlap storage.

ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS keyword_score        DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS title_score          DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS unit_score           DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS semantic_score       DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS technology_score     DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS research_area_score  DOUBLE PRECISION;
ALTER TABLE submission_similarities ADD COLUMN IF NOT EXISTS same_unit            BOOLEAN;

-- Shared structured tags per similarity row (mirrors similarity_keywords)
CREATE TABLE IF NOT EXISTS similarity_technologies (
  similarity_id BIGINT NOT NULL REFERENCES submission_similarities(id) ON DELETE CASCADE,
  technology    VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_similarity_technologies ON similarity_technologies(similarity_id);

CREATE TABLE IF NOT EXISTS similarity_research_areas (
  similarity_id BIGINT NOT NULL REFERENCES submission_similarities(id) ON DELETE CASCADE,
  research_area VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_similarity_research_areas ON similarity_research_areas(similarity_id);
