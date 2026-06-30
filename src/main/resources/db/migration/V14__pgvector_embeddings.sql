CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE submissions ADD COLUMN IF NOT EXISTS embedding vector(768);

CREATE INDEX IF NOT EXISTS idx_submissions_embedding
  ON submissions USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);
