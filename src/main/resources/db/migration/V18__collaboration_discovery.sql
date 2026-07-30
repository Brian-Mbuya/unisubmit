-- Phase 8 — AI-Powered Collaboration Discovery
-- Splits collaboration discovery away from integrity/similarity detection.

-- Broad application domains extracted by the LLM (transportation, healthcare, …)
CREATE TABLE IF NOT EXISTS ai_insight_domains (
  insight_id BIGINT NOT NULL REFERENCES ai_insights(id) ON DELETE CASCADE,
  domain     VARCHAR(120)
);
CREATE INDEX IF NOT EXISTS idx_ai_insight_domains ON ai_insight_domains(insight_id);

-- Opt-out for collaboration discovery (default: discoverable)
ALTER TABLE student_profiles
  ADD COLUMN IF NOT EXISTS discoverable_for_collaboration BOOLEAN NOT NULL DEFAULT TRUE;

-- Collaboration pairings — kept separate from submission_similarities.
CREATE TABLE IF NOT EXISTS collaboration_matches (
  id                  BIGSERIAL PRIMARY KEY,
  submission_a_id     BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  submission_b_id     BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  mechanical_score    DOUBLE PRECISION NOT NULL DEFAULT 0,
  collaboration_value VARCHAR(20) NOT NULL DEFAULT 'UNASSESSED',
  collaboration_type  VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  what_a_gains        TEXT,
  what_b_gains        TEXT,
  pitch               TEXT,
  complementary_gaps  TEXT,
  hash_a              VARCHAR(64),
  hash_b              VARCHAR(64),
  computed_at         TIMESTAMP,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_collab_pair UNIQUE (submission_a_id, submission_b_id)
);
CREATE INDEX IF NOT EXISTS idx_collab_a ON collaboration_matches(submission_a_id);
CREATE INDEX IF NOT EXISTS idx_collab_b ON collaboration_matches(submission_b_id);
CREATE INDEX IF NOT EXISTS idx_collab_value ON collaboration_matches(collaboration_value);
