-- Identical-document detection: fingerprint every uploaded file so the
-- recommendation engine can flag byte-for-byte duplicate submissions.
ALTER TABLE submission_versions ADD COLUMN content_hash VARCHAR(64);
