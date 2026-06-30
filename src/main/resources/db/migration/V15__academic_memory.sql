-- Phase 4 — Academic Memory
-- Append-only activity history + curated lineage links between submissions.

-- Append-only audit trail. No UPDATE/DELETE pathway is exposed in the application.
CREATE TABLE IF NOT EXISTS audit_logs (
  id            BIGSERIAL PRIMARY KEY,
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  action        VARCHAR(40) NOT NULL,
  detail        VARCHAR(500) NOT NULL,
  actor_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_submission ON audit_logs(submission_id);

-- Manually curated research genealogy. Distinct from automatic submission_similarities.
CREATE TABLE IF NOT EXISTS submission_relations (
  id              BIGSERIAL PRIMARY KEY,
  submission_a_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  submission_b_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  relation_type   VARCHAR(30) NOT NULL,
  created_by_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_submission_relation UNIQUE (submission_a_id, submission_b_id, relation_type)
);

CREATE INDEX IF NOT EXISTS idx_submission_relations_a ON submission_relations(submission_a_id);
CREATE INDEX IF NOT EXISTS idx_submission_relations_b ON submission_relations(submission_b_id);
