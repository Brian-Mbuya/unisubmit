package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Role;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import java.util.List;
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

    public CsvImportService(UserRepository userRepository,
                            StudentProfileRepository studentProfileRepository,
                            CourseRepository courseRepository,
                            UserService userService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.courseRepository = courseRepository;
        this.userService = userService;
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
    public StudentPreview parseStudents(MultipartFile file) {
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
                String name = get(rec, "name");
                String email = get(rec, "email").toLowerCase();
                String studentId = get(rec, "studentId");
                String programmeCode = get(rec, "programmeCode");
                String yearRaw = get(rec, "year");

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
        } catch (IOException ex) {
            return new StudentPreview(List.of(), 0, 0, "Could not read the file: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return new StudentPreview(List.of(), 0, 0, "That doesn't look like a valid CSV file.");
        }

        return new StudentPreview(rows, count(rows, true), count(rows, false), null);
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

    /** Minimal CSV field escaping for the results download. */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
