-- Preload Multidisciplinary Technologies
INSERT INTO technologies (name) VALUES
  ('Spring Boot'), ('React'), ('Docker'), ('TensorFlow'), ('EHR Systems'),
  ('GIS Mapping'), ('EEG Biosensors'), ('CAD Modeling'), ('Microscopy Imaging'),
  ('Financial Ledger'), ('Online Survey Tools'), ('Kubernetes'), ('Unity 3D'),
  ('SPSS Software'), ('MATLAB Toolkit')
ON CONFLICT (name) DO NOTHING;

-- Preload Multidisciplinary Research Areas
INSERT INTO research_areas (name) VALUES
  ('Machine Learning'), ('Software Engineering'), ('Biblical Hermeneutics'),
  ('Systematic Theology'), ('Interpersonal Relationships'), ('Clinical Psychology'),
  ('Constitutional Law'), ('Corporate Governance'), ('Pedagogy & Education'),
  ('Renewable Energy'), ('Epidemiology'), ('Microbiology'), ('Macroeconomics'),
  ('Fluid Dynamics'), ('Social Psychology')
ON CONFLICT (name) DO NOTHING;

-- Preload Common Frameworks
INSERT INTO frameworks (name) VALUES
  ('Spring Boot'), ('Next.js'), ('Django'), ('TensorFlow'),
  ('PyTorch'), ('Flutter'), ('Laravel'), ('Angular'), ('Express.js')
ON CONFLICT (name) DO NOTHING;

-- Preload Common Databases
INSERT INTO databases (name) VALUES
  ('PostgreSQL'), ('MySQL'), ('MongoDB'), ('SQLite'),
  ('Redis'), ('Oracle'), ('Cassandra'), ('Neo4j')
ON CONFLICT (name) DO NOTHING;

-- Preload Common Programming Languages
INSERT INTO programming_languages (name) VALUES
  ('Java'), ('Python'), ('JavaScript'), ('TypeScript'),
  ('R'), ('C++'), ('SQL'), ('MATLAB'), ('Kotlin'), ('Go')
ON CONFLICT (name) DO NOTHING;

-- Preload Multidisciplinary Skills
INSERT INTO skills (name) VALUES
  ('Qualitative Analysis'), ('Statistical Modeling'), ('Academic Writing'),
  ('Project Management'), ('Data Science'), ('Laboratory Safety'),
  ('Financial Analysis'), ('Theological Exegesis'), ('Counseling Techniques'),
  ('Legal Research')
ON CONFLICT (name) DO NOTHING;
