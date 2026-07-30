package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Department;
import com.unisubmit.domain.Enrollment;
import com.unisubmit.domain.Faculty;
import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.domain.Unit;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.DepartmentRepository;
import com.unisubmit.repository.EnrollmentRepository;
import com.unisubmit.repository.FacultyRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import com.unisubmit.repository.UnitRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk import for the academic structure — faculties, departments, programmes, units,
 * curriculum rows, teaching assignments and enrolments.
 *
 * <h2>Why one service and not seven</h2>
 * {@link CsvImportService} needed ~180 lines per entity (CSV parser + XLSX parser +
 * validator + applier + template + preview record). Seven more copies of that would be
 * over a thousand lines that all drift independently. Here the format handling, magic-byte
 * gate, row cap and preview/apply split are written once, and each {@link Kind} contributes
 * only two things: the columns it needs, and what one validated row means.
 *
 * <h2>Two-step, same as the student importer</h2>
 * {@link #parse} validates and writes nothing (drives the preview screen); {@link #apply}
 * consumes the already-validated rows. Rows are Serializable so the controller can stash
 * them in the session between the two steps and never re-read the upload.
 *
 * <h2>Idempotent by design</h2>
 * Every applier is find-or-create keyed on the natural code. Re-importing the same file is
 * a no-op rather than a duplicate — which matters because the realistic admin workflow is
 * "export from the SIS, fix three rows, re-upload the whole thing".
 */
@Service
public class AcademicImportService {

    private static final Logger log = LoggerFactory.getLogger(AcademicImportService.class);
    private static final int MAX_ROWS = 5000;

    /**
     * The importable entity types. {@code columns} is both the required-header list and
     * the template header, so the two can never disagree.
     */
    public enum Kind {
        FACULTY("Faculties", List.of("code", "name"),
                "SCI,Faculty of Science\nENG,Faculty of Engineering"),
        DEPARTMENT("Departments", List.of("code", "name", "facultycode"),
                "CS,Computer Science,SCI\nMATH,Mathematics,SCI"),
        PROGRAMME("Programmes", List.of("code", "name", "departmentcode"),
                "BCS,BSc Computer Science,CS\nBIT,BSc Information Technology,CS"),
        UNIT("Units", List.of("code", "name", "departmentcode"),
                "CS301,Machine Learning,CS\nCS302,Distributed Systems,CS"),
        CURRICULUM("Curriculum", List.of("programmecode", "unitcode", "year", "semester"),
                "BCS,CS301,3,1\nBCS,CS302,3,1"),
        TEACHING_ASSIGNMENT("Teaching assignments",
                List.of("staffid", "programmecode", "unitcode", "year", "semester"),
                "STAFF-001,BCS,CS301,3,1\nSTAFF-002,BCS,CS302,3,1"),
        ENROLLMENT("Enrolments",
                List.of("admissionnumber", "programmecode", "unitcode", "year", "semester"),
                "SCT-001,BCS,CS301,3,1\nSCT-002,BCS,CS301,3,1");

        private final String label;
        private final List<String> columns;
        private final String sampleRows;

        Kind(String label, List<String> columns, String sampleRows) {
            this.label = label;
            this.columns = columns;
            this.sampleRows = sampleRows;
        }

        public String getLabel() { return label; }
        public List<String> getColumns() { return columns; }
        public String header() { return String.join(",", columns); }
        public String templateCsv() { return header() + "\n" + sampleRows + "\n"; }
    }

    /** One parsed row. {@code values} is keyed by lower-case column name. */
    public record AcademicRow(int line, Map<String, String> values, boolean valid, String error)
            implements Serializable {}

    public record AcademicPreview(String kind, List<AcademicRow> rows, int validCount,
                                  int invalidCount, String fatalError) implements Serializable {}

    public record ApplyResult(int created, int skipped, int failed, List<String> messages) {}

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final CurriculumRepository curriculumRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final StudentProfileRepository studentProfileRepository;

    public AcademicImportService(FacultyRepository facultyRepository,
                                 DepartmentRepository departmentRepository,
                                 CourseRepository courseRepository,
                                 UnitRepository unitRepository,
                                 CurriculumRepository curriculumRepository,
                                 TeachingAssignmentRepository teachingAssignmentRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 LecturerProfileRepository lecturerProfileRepository,
                                 StudentProfileRepository studentProfileRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.curriculumRepository = curriculumRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    // ── Step 1: parse + validate (no writes) ────────────────────────────────────

    public AcademicPreview parse(Kind kind, MultipartFile file) {
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase();
        return (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
                ? parseWorkbook(kind, file) : parseCsv(kind, file);
    }

    private AcademicPreview parseCsv(Kind kind, MultipartFile file) {
        List<AcademicRow> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            var headers = parser.getHeaderMap().keySet();
            String missing = firstMissingColumn(kind, headers);
            if (missing != null) return fatal(kind, missing);

            for (CSVRecord rec : parser) {
                if (rows.size() >= MAX_ROWS) {
                    return new AcademicPreview(kind.name(), rows, count(rows, true), count(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String col : kind.getColumns()) {
                    values.put(col, safeGet(rec, col));
                }
                rows.add(validate(kind, (int) rec.getRecordNumber() + 1, values));
            }
        } catch (IOException ex) {
            return new AcademicPreview(kind.name(), List.of(), 0, 0,
                    "Could not read the file: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return new AcademicPreview(kind.name(), List.of(), 0, 0,
                    "That doesn't look like a valid CSV file.");
        }
        return new AcademicPreview(kind.name(), rows, count(rows, true), count(rows, false), null);
    }

    private AcademicPreview parseWorkbook(Kind kind, MultipartFile file) {
        // Magic-byte gate BEFORE POI touches the stream — same guard as CsvImportService,
        // which exists because POI happily buffers and parses hostile input.
        try (InputStream in = file.getInputStream()) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            boolean xlsx = read >= 4 && magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            boolean xls = read >= 4 && (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
            if (!xlsx && !xls) {
                return new AcademicPreview(kind.name(), List.of(), 0, 0,
                        "That doesn't look like a valid .xlsx or .xls file.");
            }
        } catch (IOException ex) {
            return new AcademicPreview(kind.name(), List.of(), 0, 0,
                    "Could not read the file: " + ex.getMessage());
        }

        List<AcademicRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return new AcademicPreview(kind.name(), List.of(), 0, 0, "The spreadsheet has no sheets.");
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return fatal(kind, kind.getColumns().get(0));

            Map<String, Integer> col = new HashMap<>();
            for (Cell cell : header) col.put(cellString(cell).toLowerCase(), cell.getColumnIndex());

            String missing = firstMissingColumn(kind, col.keySet());
            if (missing != null) return fatal(kind, missing);

            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                if (rows.size() >= MAX_ROWS) {
                    return new AcademicPreview(kind.name(), rows, count(rows, true), count(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                for (String c : kind.getColumns()) {
                    Integer idx = col.get(c);
                    Cell cell = idx == null ? null : row.getCell(idx);
                    values.put(c, cell == null ? "" : cellString(cell));
                }
                if (values.values().stream().allMatch(String::isBlank)) continue; // blank row
                rows.add(validate(kind, r + 1, values));
            }
        } catch (Exception ex) {
            log.warn("Academic spreadsheet parse failed: {}", ex.getMessage());
            return new AcademicPreview(kind.name(), List.of(), 0, 0,
                    "That doesn't look like a valid .xlsx or .xls file.");
        }
        return new AcademicPreview(kind.name(), rows, count(rows, true), count(rows, false), null);
    }

    /**
     * Per-row validation. Only checks what can be checked WITHOUT writing: required
     * fields, numeric fields, and that referenced parents already exist. Whether the row
     * itself is a duplicate is decided at apply time by the find-or-create appliers, so
     * a re-import previews as valid and applies as "skipped" rather than failing.
     */
    private AcademicRow validate(Kind kind, int line, Map<String, String> values) {
        for (String col : kind.getColumns()) {
            if (values.getOrDefault(col, "").isBlank()) {
                return new AcademicRow(line, values, false, "'" + col + "' is required");
            }
        }
        String error = switch (kind) {
            case FACULTY -> null;
            case DEPARTMENT -> facultyRepository.findByCodeIgnoreCase(values.get("facultycode")).isPresent()
                    ? null : "Unknown faculty code '" + values.get("facultycode") + "'";
            case PROGRAMME, UNIT -> departmentRepository.findByCodeIgnoreCase(values.get("departmentcode")).isPresent()
                    ? null : "Unknown department code '" + values.get("departmentcode") + "'";
            case CURRICULUM -> curriculumRefError(values);
            case TEACHING_ASSIGNMENT -> {
                String base = curriculumRefError(values);
                if (base != null) yield base;
                yield lecturerProfileRepository.findByStaffNumberIgnoreCase(values.get("staffid")).isPresent()
                        ? null : "Unknown staff ID '" + values.get("staffid") + "'";
            }
            case ENROLLMENT -> {
                String base = curriculumRefError(values);
                if (base != null) yield base;
                yield studentProfileRepository.findByAdmissionNumberIgnoreCase(values.get("admissionnumber")).isPresent()
                        ? null : "Unknown admission number '" + values.get("admissionnumber") + "'";
            }
        };
        return new AcademicRow(line, values, error == null, error);
    }

    /** Shared by the three kinds keyed on (programme, unit, year, semester). */
    private String curriculumRefError(Map<String, String> v) {
        if (courseRepository.findByCodeIgnoreCase(v.get("programmecode")).isEmpty()) {
            return "Unknown programme code '" + v.get("programmecode") + "'";
        }
        if (unitRepository.findByUnitCodeIgnoreCase(v.get("unitcode")).isEmpty()) {
            return "Unknown unit code '" + v.get("unitcode") + "'";
        }
        if (parseInt(v.get("year")) == null) return "Year must be a number";
        if (parseInt(v.get("semester")) == null) return "Semester must be a number";
        return null;
    }

    // ── Step 2: apply validated rows ────────────────────────────────────────────

    /**
     * Applies row by row in its own transaction boundary per row via the repository
     * calls — one bad row is reported and skipped rather than rolling back the batch,
     * matching how the student importer behaves.
     */
    @Transactional
    public ApplyResult apply(Kind kind, List<AcademicRow> rows) {
        int created = 0, skipped = 0, failed = 0;
        List<String> messages = new ArrayList<>();

        for (AcademicRow row : rows) {
            if (!row.valid()) continue;
            try {
                boolean didCreate = switch (kind) {
                    case FACULTY -> applyFaculty(row.values());
                    case DEPARTMENT -> applyDepartment(row.values());
                    case PROGRAMME -> applyProgramme(row.values());
                    case UNIT -> applyUnit(row.values());
                    case CURRICULUM -> applyCurriculum(row.values()) != null;
                    case TEACHING_ASSIGNMENT -> applyTeachingAssignment(row.values());
                    case ENROLLMENT -> applyEnrollment(row.values());
                };
                if (didCreate) created++; else skipped++;
            } catch (Exception ex) {
                failed++;
                messages.add("Line " + row.line() + ": " + ex.getMessage());
                log.warn("Academic import ({}) line {} failed: {}", kind, row.line(), ex.getMessage());
            }
        }
        log.info("Academic import {}: {} created, {} already existed, {} failed",
                kind, created, skipped, failed);
        return new ApplyResult(created, skipped, failed, messages);
    }

    private boolean applyFaculty(Map<String, String> v) {
        if (facultyRepository.findByCodeIgnoreCase(v.get("code")).isPresent()) return false;
        Faculty f = new Faculty();
        f.setCode(v.get("code"));
        f.setName(v.get("name"));
        facultyRepository.save(f);
        return true;
    }

    private boolean applyDepartment(Map<String, String> v) {
        if (departmentRepository.findByCodeIgnoreCase(v.get("code")).isPresent()) return false;
        Department d = new Department();
        d.setCode(v.get("code"));
        d.setName(v.get("name"));
        d.setFaculty(facultyRepository.findByCodeIgnoreCase(v.get("facultycode")).orElseThrow());
        departmentRepository.save(d);
        return true;
    }

    private boolean applyProgramme(Map<String, String> v) {
        if (courseRepository.findByCodeIgnoreCase(v.get("code")).isPresent()) return false;
        Course c = new Course();
        c.setCode(v.get("code"));
        c.setName(v.get("name"));
        c.setDepartment(departmentRepository.findByCodeIgnoreCase(v.get("departmentcode")).orElseThrow());
        courseRepository.save(c);
        return true;
    }

    private boolean applyUnit(Map<String, String> v) {
        if (unitRepository.findByUnitCodeIgnoreCase(v.get("code")).isPresent()) return false;
        Unit u = new Unit();
        u.setUnitCode(v.get("code"));
        u.setUnitName(v.get("name"));
        u.setDepartment(departmentRepository.findByCodeIgnoreCase(v.get("departmentcode")).orElseThrow());
        unitRepository.save(u);
        return true;
    }

    /** Returns the curriculum row, creating it if absent — the other two kinds reuse this. */
    private Curriculum applyCurriculum(Map<String, String> v) {
        Course programme = courseRepository.findByCodeIgnoreCase(v.get("programmecode")).orElseThrow();
        Unit unit = unitRepository.findByUnitCodeIgnoreCase(v.get("unitcode")).orElseThrow();
        Integer year = parseInt(v.get("year"));
        Integer semester = parseInt(v.get("semester"));

        return curriculumRepository
                .findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                        programme.getId(), unit.getId(), year, semester)
                .orElseGet(() -> {
                    Curriculum c = new Curriculum();
                    c.setProgramme(programme);
                    c.setUnit(unit);
                    c.setYearOfStudy(year);
                    c.setSemesterNumber(semester);
                    return curriculumRepository.save(c);
                });
    }

    private boolean applyTeachingAssignment(Map<String, String> v) {
        Curriculum curriculum = applyCurriculum(v);
        LecturerProfile lecturer = lecturerProfileRepository
                .findByStaffNumberIgnoreCase(v.get("staffid")).orElseThrow();
        if (teachingAssignmentRepository
                .findByLecturerIdAndCurriculumId(lecturer.getId(), curriculum.getId()).isPresent()) {
            return false;
        }
        TeachingAssignment ta = new TeachingAssignment();
        ta.setLecturer(lecturer);
        ta.setCurriculum(curriculum);
        teachingAssignmentRepository.save(ta);
        return true;
    }

    private boolean applyEnrollment(Map<String, String> v) {
        Curriculum curriculum = applyCurriculum(v);
        StudentProfile student = studentProfileRepository
                .findByAdmissionNumberIgnoreCase(v.get("admissionnumber")).orElseThrow();
        if (enrollmentRepository
                .findByStudentIdAndCurriculumId(student.getId(), curriculum.getId()).isPresent()) {
            return false;
        }
        Enrollment e = new Enrollment();
        e.setStudent(student);
        e.setCurriculum(curriculum);
        e.setStatus("ENROLLED");
        enrollmentRepository.save(e);
        return true;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private AcademicPreview fatal(Kind kind, String missingColumn) {
        return new AcademicPreview(kind.name(), List.of(), 0, 0,
                "Missing required column '" + missingColumn + "'. Expected header: " + kind.header());
    }

    private static String firstMissingColumn(Kind kind, java.util.Collection<String> headers) {
        for (String required : kind.getColumns()) {
            boolean present = headers.stream().anyMatch(h -> h.equalsIgnoreCase(required));
            if (!present) return required;
        }
        return null;
    }

    private static int count(List<AcademicRow> rows, boolean valid) {
        return (int) rows.stream().filter(r -> r.valid() == valid).count();
    }

    private static Integer parseInt(String raw) {
        try { return Integer.parseInt(raw.trim()); }
        catch (Exception ex) { return null; }
    }

    private static String safeGet(CSVRecord rec, String col) {
        try { return rec.isMapped(col) && rec.get(col) != null ? rec.get(col).trim() : ""; }
        catch (IllegalArgumentException ex) { return ""; }
    }

    /** Numeric cells render without a trailing ".0" so "3" does not become "3.0". */
    private static String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                        ? String.valueOf((long) d) : String.valueOf(d);
            }
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield ""; }
            }
            default -> "";
        };
    }
}
