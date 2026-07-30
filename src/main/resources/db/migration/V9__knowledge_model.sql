CREATE TABLE IF NOT EXISTS technologies (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS research_areas (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS submission_technologies (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  technology_id BIGINT NOT NULL REFERENCES technologies(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, technology_id)
);

CREATE TABLE IF NOT EXISTS submission_research_areas (
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  research_area_id BIGINT NOT NULL REFERENCES research_areas(id) ON DELETE CASCADE,
  PRIMARY KEY (submission_id, research_area_id)
);

CREATE TABLE IF NOT EXISTS "references" (
  id BIGSERIAL PRIMARY KEY,
  submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  authors TEXT,
  title TEXT NOT NULL,
  journal VARCHAR(255),
  year VARCHAR(10),
  doi VARCHAR(255)
);

-- SEED: Technologies
INSERT INTO technologies (name) VALUES
('Java'),('Spring Boot'),('Python'),('Django'),('React'),
('Angular'),('Vue.js'),('Node.js'),('Express'),('Flutter'),
('Android'),('PostgreSQL'),('MySQL'),('MongoDB'),('Redis'),
('Docker'),('Kubernetes'),('TensorFlow'),('PyTorch'),('Keras'),
('Scikit-learn'),('AWS'),('Firebase'),('Git'),('Linux'),
('PHP'),('Laravel'),('TypeScript'),('GraphQL'),('REST API')
ON CONFLICT (name) DO NOTHING;

-- SEED: Research Areas
INSERT INTO research_areas (name) VALUES
('Machine Learning'),('Web Development'),('Mobile Development'),
('Cybersecurity'),('Data Science'),('Cloud Computing'),
('Internet of Things'),('Blockchain'),('Natural Language Processing'),
('Computer Vision'),('Database Systems'),('Software Engineering'),
('Human Computer Interaction'),('Network Security'),('Artificial Intelligence')
ON CONFLICT (name) DO NOTHING;
