package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Role;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Admin bulk import from CSV. Two-step by design: {@link #parseStudents} validates
 * every row WITHOUT writing anything (drives the preview), then {@link #applyStudents}
 * creates the accounts from the already-validated rows. Passwords are generated per
 * student and returned once so the admin can distribute them — they are never stored
 * in plaintext.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final int MAX_ROWS = 2000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Unambiguous alphabet — no 0/O/1/l/I — so hand-distributed passwords aren't misread.
    private static final char[] PW_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final UserService userService;
    private final com.unisubmit.repository.LecturerProfileRepository lecturerProfileRepository;
    private final com.unisubmit.repository.DepartmentRepository departmentRepository;

    public CsvImportService(UserRepository userRepository,
                            StudentProfileRepository studentProfileRepository,
                            CourseRepository courseRepository,
                            UserService userService,
                            com.unisubmit.repository.LecturerProfileRepository lecturerProfileRepository,
                            com.unisubmit.repository.DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.departmentRepository = departmentRepository;
    }

    // ── Data carriers (Serializable so they survive in the HTTP session) ────────
    public record StudentRow(int line, String name, String email, String studentId,
                             String programmeCode, Integer year, Long programmeId,
                             boolean valid, String error) implements Serializable {}

    public record StudentPreview(List<StudentRow> rows, int validCount, int invalidCount,
                                 String fatalError) implements Serializable {}

    public record CreatedCredential(String name, String studentId, String email,
                                    String password, String status) implements Serializable {}

    // ── Step 1: parse + validate, no writes ─────────────────────────────────────
    /** Single entry point — dispatches by extension so the controller stays simple. */
    public StudentPreview parseStudents(MultipartFile file) {
        String fn = file.getOriginalFilename();
        String lower = fn == null ? "" : fn.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseStudentsWorkbook(file);
        }
        return parseStudentsCsv(file);
    }

    private StudentPreview parseStudentsCsv(MultipartFile file) {
        List<StudentRow> rows = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenIds = new HashSet<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            var headers = parser.getHeaderMap().keySet();
            for (String required : List.of("name", "email", "studentid")) {
                boolean present = headers.stream().anyMatch(h -> h.equalsIgnoreCase(required));
                if (!present) {
                    return new StudentPreview(List.of(), 0, 0,
                            "Missing required column '" + required + "'. Expected header: "
                                    + "name, email, studentId, programmeCode, year");
                }
            }

            for (CSVRecord rec : parser) {
                if (rows.size() >= MAX_ROWS) {
                    return new StudentPreview(rows, count(rows, true), count(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                int line = (int) rec.getRecordNumber() + 1; // +1 for the header row
                validateAndAdd(rows, seenEmails, seenIds, line,
                        get(rec, "name"), get(rec, "email"), get(rec, "studentId"),
                        get(rec, "programmeCode"), get(rec, "year"));
            }
        } catch (IOException ex) {
            return new StudentPreview(List.of(), 0, 0, "Could not read the file: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return new StudentPreview(List.of(), 0, 0, "That doesn't look like a valid CSV file.");
        }

        return new StudentPreview(rows, count(rows, true), count(rows, false), null);
    }

    // ── Shared per-row validation (CSV + XLSX feed the exact same rules) ────────
    private void validateAndAdd(List<StudentRow> rows, Set<String> seenEmails, Set<String> seenIds,
                                int line, String name, String email, String studentId,
                                String programmeCode, String yearRaw) {
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim().toLowerCase();
        studentId = studentId == null ? "" : studentId.trim();
        programmeCode = programmeCode == null ? "" : programmeCode.trim();
        yearRaw = yearRaw == null ? "" : yearRaw.trim();

        String error = null;
        Integer year = null;
        Long programmeId = null;

        if (name.isBlank()) error = "Name is required";
        else if (email.isBlank() || !EMAIL.matcher(email).matches()) error = "Invalid or missing email";
        else if (studentId.isBlank()) error = "Student ID is required";
        else if (!seenEmails.add(email)) error = "Duplicate email in this file";
        else if (!seenIds.add(studentId.toLowerCase())) error = "Duplicate Student ID in this file";
        else if (userRepository.findByUsername(email).isPresent()) error = "Email already registered";
        else if (studentProfileRepository.findByAdmissionNumberIgnoreCase(studentId).isPresent())
            error = "Student ID already registered";

        if (error == null && !programmeCode.isBlank()) {
            Course c = courseRepository.findByCodeIgnoreCase(programmeCode).orElse(null);
            if (c == null) error = "Unknown programme code '" + programmeCode + "'";
            else programmeId = c.getId();
        }
        if (error == null && !yearRaw.isBlank()) {
            try { year = Integer.parseInt(yearRaw); }
            catch (NumberFormatException ex) { error = "Year must be a number"; }
        }

        rows.add(new StudentRow(line, name, email, studentId, programmeCode, year,
                programmeId, error == null, error));
    }

    // ── Step 1b: parse + validate an .xlsx workbook (first sheet) ───────────────
    public StudentPreview parseStudentsWorkbook(MultipartFile file) {
        List<StudentRow> rows = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenIds = new HashSet<>();

        // Magic-byte gate BEFORE handing the file to POI (which otherwise buffers/parses
        // hostile input). Accept the two real spreadsheet signatures: .xlsx is a ZIP
        // ("PK\x03\x04"); .xls is an OLE2 compound file (0xD0 0xCF 0x11 0xE0).
        try (java.io.InputStream in = file.getInputStream()) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            boolean xlsx = read >= 4 && magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            boolean xls = read >= 4 && (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
            if (!xlsx && !xls) {
                return new StudentPreview(List.of(), 0, 0,
                        "That doesn't look like a valid .xlsx or .xls file.");
            }
        } catch (IOException ex) {
            return new StudentPreview(List.of(), 0, 0, "Could not read the file: " + ex.getMessage());
        }

        // WorkbookFactory auto-detects .xls (HSSF) vs .xlsx (XSSF).
        try (Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return new StudentPreview(List.of(), 0, 0, "The spreadsheet has no sheets.");

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return new StudentPreview(List.of(), 0, 0,
                    "The first row must be a header: name, email, studentId, programmeCode, year");

            Map<String, Integer> col = new HashMap<>();
            for (Cell cell : header) col.put(cellString(cell).toLowerCase(), cell.getColumnIndex());

            for (String required : List.of("name", "email", "studentid")) {
                if (!col.containsKey(required)) {
                    return new StudentPreview(List.of(), 0, 0,
                            "Missing required column '" + required + "'. Expected header: "
                                    + "name, email, studentId, programmeCode, year");
                }
            }

            int last = sheet.getLastRowNum();
            for (int r = header.getRowNum() + 1; r <= last; r++) {
                if (rows.size() >= MAX_ROWS) {
                    return new StudentPreview(rows, count(rows, true), count(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = colVal(row, col, "name");
                String email = colVal(row, col, "email");
                String studentId = colVal(row, col, "studentid");
                if (name.isBlank() && email.isBlank() && studentId.isBlank()) continue; // skip blank rows
                validateAndAdd(rows, seenEmails, seenIds, r + 1, name, email, studentId,
                        colVal(row, col, "programmecode"), colVal(row, col, "year"));
            }
        } catch (Exception ex) {
            log.warn("Spreadsheet import parse failed: {}", ex.getMessage());
            return new StudentPreview(List.of(), 0, 0, "That doesn't look like a valid .xlsx or .xls file.");
        }

        return new StudentPreview(rows, count(rows, true), count(rows, false), null);
    }

    private static String colVal(Row row, Map<String, Integer> col, String key) {
        Integer idx = col.get(key);
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        return cell == null ? "" : cellString(cell);
    }

    /** Best-effort cell → string; numeric IDs/years render without a trailing ".0". */
    private static String cellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case NUMERIC:
                double d = cell.getNumericCellValue();
                return (d == Math.floor(d) && !Double.isInfinite(d))
                        ? String.valueOf((long) d) : String.valueOf(d);
            case FORMULA:
                try { return cell.getStringCellValue().trim(); }
                catch (Exception e) { return ""; }
            default: return "";
        }
    }

    // ── Step 2: create accounts from validated rows (per-row, partial-safe) ─────
    public List<CreatedCredential> applyStudents(List<StudentRow> validRows) {
        List<CreatedCredential> results = new ArrayList<>();
        for (StudentRow r : validRows) {
            if (!r.valid()) continue;
            String password = generatePassword();
            try {
                // username = email (students sign in with email or Student ID)
                userService.createUser(r.email(), password, r.name(), Role.STUDENT,
                        r.studentId(), null, null, r.programmeId(), r.year(), null);
                results.add(new CreatedCredential(r.name(), r.studentId(), r.email(), password, "created"));
            } catch (Exception ex) {
                log.warn("CSV import: row {} ({}) failed: {}", r.line(), r.email(), ex.getMessage());
                results.add(new CreatedCredential(r.name(), r.studentId(), r.email(), "—",
                        "failed: " + ex.getMessage()));
            }
        }
        log.info("CSV student import: {} rows processed, {} created",
                validRows.size(), results.stream().filter(c -> "created".equals(c.status())).count());
        return results;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Lecturer import — mirrors the student flow (preview → apply → one-shot creds)
    // ════════════════════════════════════════════════════════════════════════════

    public record LecturerRow(int line, String name, String email, String staffId,
                              String departmentCode, Long departmentId,
                              boolean valid, String error) implements Serializable {}

    public record LecturerPreview(List<LecturerRow> rows, int validCount, int invalidCount,
                                  String fatalError) implements Serializable {}

    /** Single entry point — dispatches by extension, exactly like the student importer. */
    public LecturerPreview parseLecturers(MultipartFile file) {
        String fn = file.getOriginalFilename();
        String lower = fn == null ? "" : fn.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseLecturersWorkbook(file);
        }
        return parseLecturersCsv(file);
    }

    private LecturerPreview parseLecturersCsv(MultipartFile file) {
        List<LecturerRow> rows = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenIds = new HashSet<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            var headers = parser.getHeaderMap().keySet();
            for (String required : List.of("name", "email", "staffid")) {
                boolean present = headers.stream().anyMatch(h -> h.equalsIgnoreCase(required));
                if (!present) {
                    return new LecturerPreview(List.of(), 0, 0,
                            "Missing required column '" + required + "'. Expected header: "
                                    + "name, email, staffId, departmentCode");
                }
            }

            for (CSVRecord rec : parser) {
                if (rows.size() >= MAX_ROWS) {
                    return new LecturerPreview(rows, countLecturers(rows, true), countLecturers(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                int line = (int) rec.getRecordNumber() + 1;
                validateAndAddLecturer(rows, seenEmails, seenIds, line,
                        get(rec, "name"), get(rec, "email"), get(rec, "staffId"),
                        get(rec, "departmentCode"));
            }
        } catch (IOException ex) {
            return new LecturerPreview(List.of(), 0, 0, "Could not read the file: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return new LecturerPreview(List.of(), 0, 0, "That doesn't look like a valid CSV file.");
        }

        return new LecturerPreview(rows, countLecturers(rows, true), countLecturers(rows, false), null);
    }

    private LecturerPreview parseLecturersWorkbook(MultipartFile file) {
        List<LecturerRow> rows = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenIds = new HashSet<>();

        try (java.io.InputStream in = file.getInputStream()) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            boolean xlsx = read >= 4 && magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            boolean xls = read >= 4 && (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
            if (!xlsx && !xls) {
                return new LecturerPreview(List.of(), 0, 0,
                        "That doesn't look like a valid .xlsx or .xls file.");
            }
        } catch (IOException ex) {
            return new LecturerPreview(List.of(), 0, 0, "Could not read the file: " + ex.getMessage());
        }

        try (Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return new LecturerPreview(List.of(), 0, 0, "The spreadsheet has no sheets.");

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return new LecturerPreview(List.of(), 0, 0,
                    "The first row must be a header: name, email, staffId, departmentCode");

            Map<String, Integer> col = new HashMap<>();
            for (Cell cell : header) col.put(cellString(cell).toLowerCase(), cell.getColumnIndex());

            for (String required : List.of("name", "email", "staffid")) {
                if (!col.containsKey(required)) {
                    return new LecturerPreview(List.of(), 0, 0,
                            "Missing required column '" + required + "'. Expected header: "
                                    + "name, email, staffId, departmentCode");
                }
            }

            int last = sheet.getLastRowNum();
            for (int r = header.getRowNum() + 1; r <= last; r++) {
                if (rows.size() >= MAX_ROWS) {
                    return new LecturerPreview(rows, countLecturers(rows, true), countLecturers(rows, false),
                            "File has more than " + MAX_ROWS + " rows — split it and import in batches.");
                }
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = colVal(row, col, "name");
                String email = colVal(row, col, "email");
                String staffId = colVal(row, col, "staffid");
                if (name.isBlank() && email.isBlank() && staffId.isBlank()) continue;
                validateAndAddLecturer(rows, seenEmails, seenIds, r + 1, name, email, staffId,
                        colVal(row, col, "departmentcode"));
            }
        } catch (Exception ex) {
            log.warn("Lecturer spreadsheet import parse failed: {}", ex.getMessage());
            return new LecturerPreview(List.of(), 0, 0, "That doesn't look like a valid .xlsx or .xls file.");
        }

        return new LecturerPreview(rows, countLecturers(rows, true), countLecturers(rows, false), null);
    }

    private void validateAndAddLecturer(List<LecturerRow> rows, Set<String> seenEmails, Set<String> seenIds,
                                        int line, String name, String email, String staffId,
                                        String departmentCode) {
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim().toLowerCase();
        staffId = staffId == null ? "" : staffId.trim();
        departmentCode = departmentCode == null ? "" : departmentCode.trim();

        String error = null;
        Long departmentId = null;

        if (name.isBlank()) error = "Name is required";
        else if (email.isBlank() || !EMAIL.matcher(email).matches()) error = "Invalid or missing email";
        else if (staffId.isBlank()) error = "Staff ID is required";
        else if (!seenEmails.add(email)) error = "Duplicate email in this file";
        else if (!seenIds.add(staffId.toLowerCase())) error = "Duplicate Staff ID in this file";
        else if (userRepository.findByUsername(email).isPresent()) error = "Email already registered";
        else if (lecturerProfileRepository.findByStaffNumberIgnoreCase(staffId).isPresent())
            error = "Staff ID already registered";

        if (error == null && !departmentCode.isBlank()) {
            com.unisubmit.domain.Department d =
                    departmentRepository.findByCodeIgnoreCase(departmentCode).orElse(null);
            if (d == null) error = "Unknown department code '" + departmentCode + "'";
            else departmentId = d.getId();
        }

        rows.add(new LecturerRow(line, name, email, staffId, departmentCode, departmentId,
                error == null, error));
    }

    public List<CreatedCredential> applyLecturers(List<LecturerRow> validRows) {
        List<CreatedCredential> results = new ArrayList<>();
        for (LecturerRow r : validRows) {
            if (!r.valid()) continue;
            String password = generatePassword();
            try {
                // username = email; lecturers can also sign in with their staff ID.
                userService.createUser(r.email(), password, r.name(), Role.LECTURER,
                        null, r.staffId(), r.departmentId(), null, null, null);
                results.add(new CreatedCredential(r.name(), r.staffId(), r.email(), password, "created"));
            } catch (Exception ex) {
                log.warn("Lecturer import: row {} ({}) failed: {}", r.line(), r.email(), ex.getMessage());
                results.add(new CreatedCredential(r.name(), r.staffId(), r.email(), "—",
                        "failed: " + ex.getMessage()));
            }
        }
        log.info("Lecturer import: {} rows processed, {} created",
                validRows.size(), results.stream().filter(c -> "created".equals(c.status())).count());
        return results;
    }

    public String lecturerTemplateCsv() {
        return "name,email,staffId,departmentCode\n"
                + "Dr Achieng Odhiambo,achieng.odhiambo@university.edu,STAFF-001,YOUR-DEPT-CODE\n"
                + "Prof Kamau Njoroge,kamau.njoroge@university.edu,STAFF-002,YOUR-DEPT-CODE\n";
    }

    public String lecturerCredentialsCsv(List<CreatedCredential> creds) {
        StringBuilder sb = new StringBuilder("name,staffId,email,password,status\n");
        for (CreatedCredential c : creds) {
            sb.append(csv(c.name())).append(',').append(csv(c.studentId())).append(',')
              .append(csv(c.email())).append(',').append(csv(c.password())).append(',')
              .append(csv(c.status())).append('\n');
        }
        return sb.toString();
    }

    private static int countLecturers(List<LecturerRow> rows, boolean valid) {
        return (int) rows.stream().filter(r -> r.valid() == valid).count();
    }

    // ── CSV output helpers ──────────────────────────────────────────────────────
    public String studentTemplateCsv() {
        return "name,email,studentId,programmeCode,year\n"
                + "Jane Wanjiku,jane.wanjiku@university.edu,SCT-001,DEMO-BCS,3\n"
                + "John Otieno,john.otieno@university.edu,SCT-002,DEMO-BCS,2\n";
    }

    public String credentialsCsv(List<CreatedCredential> creds) {
        StringBuilder sb = new StringBuilder("name,studentId,email,password,status\n");
        for (CreatedCredential c : creds) {
            sb.append(csv(c.name())).append(',').append(csv(c.studentId())).append(',')
              .append(csv(c.email())).append(',').append(csv(c.password())).append(',')
              .append(csv(c.status())).append('\n');
        }
        return sb.toString();
    }

    // ── internals ───────────────────────────────────────────────────────────────
    private static int count(List<StudentRow> rows, boolean valid) {
        return (int) rows.stream().filter(r -> r.valid() == valid).count();
    }

    private static String get(CSVRecord rec, String col) {
        try { return rec.isMapped(col) && rec.get(col) != null ? rec.get(col).trim() : ""; }
        catch (IllegalArgumentException ex) { return ""; }
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(PW_ALPHABET[RANDOM.nextInt(PW_ALPHABET.length)]);
        return sb.toString();
    }

    /** Delegates to the shared {@link com.unisubmit.util.CsvUtil#escape} escaper. */
    private static String csv(String v) {
        return com.unisubmit.util.CsvUtil.escape(v);
    }
}
