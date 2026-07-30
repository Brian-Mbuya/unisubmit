-- V13__add_academic_year_and_semester_to_assignments.sql
ALTER TABLE teaching_assignments ADD COLUMN academic_year VARCHAR(255);
ALTER TABLE teaching_assignments ADD COLUMN semester VARCHAR(255);
