CREATE TABLE IF NOT EXISTS frameworks (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS databases (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS programming_languages (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS skills (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS submission_frameworks (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  framework_id BIGINT NOT NULL REFERENCES frameworks(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, framework_id)
);

CREATE TABLE IF NOT EXISTS submission_databases (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  database_id BIGINT NOT NULL REFERENCES databases(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, database_id)
);

CREATE TABLE IF NOT EXISTS submission_programming_languages (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  programming_language_id BIGINT NOT NULL REFERENCES programming_languages(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, programming_language_id)
);

CREATE TABLE IF NOT EXISTS submission_skills (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, skill_id)
);
