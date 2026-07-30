package com.unisubmit.controller.admin;

import com.unisubmit.service.AcademicImportService;
import com.unisubmit.service.CsvImportService;
import com.unisubmit.service.CsvImportService.CreatedCredential;
import com.unisubmit.service.CsvImportService.StudentPreview;
import com.unisubmit.service.CsvImportService.StudentRow;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin bulk import. Flow: upload → preview (validated, nothing written) →
 * apply → one-time credentials results. Everything under /admin/** already
 * requires ROLE_ADMIN (see SecurityConfig). Parsed rows and generated
 * credentials are stashed in the HTTP session between steps so the file is
 * never re-parsed on apply.
 */
@Controller
@RequestMapping("/admin/import")
public class AdminImportController {

    private static final String SESSION_ROWS = "importStudentRows";
    private static final String SESSION_CREDS = "importStudentCreds";
    private static final String SESSION_LECTURER_ROWS = "importLecturerRows";
    private static final String SESSION_LECTURER_CREDS = "importLecturerCreds";

    private final CsvImportService csvImportService;
    private final AcademicImportService academicImportService;

    public AdminImportController(CsvImportService csvImportService,
                                 AcademicImportService academicImportService) {
        this.csvImportService = csvImportService;
        this.academicImportService = academicImportService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("activeMenu", "import");
        model.addAttribute("academicKinds", AcademicImportService.Kind.values());
        return "admin/import";
    }

    /** Handles direct browser navigation to /admin/import/students/preview (GET), which has no content — redirect gracefully. */
    @GetMapping("/students/preview")
    public String previewGet() {
        return "redirect:/admin/import";
    }

    @PostMapping("/students/preview")
    public String previewStudents(@RequestParam("file") MultipartFile file,
                                  HttpSession session, Model model, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Choose a CSV file first.");
            return "redirect:/admin/import";
        }
        // Reject oversize uploads BEFORE parsing, so a huge file can never OOM the node.
        if (file.getSize() > 5L * 1024 * 1024) {
            ra.addFlashAttribute("errorMessage", "File too large — max 5 MB; split it and import in batches.");
            return "redirect:/admin/import";
        }
        StudentPreview preview = csvImportService.parseStudents(file);
        if (preview.fatalError() != null) {
            // A fatal-error preview (bad file, missing column, row cap) must NEVER leave
            // applicable rows in the session — otherwise a direct POST to apply could import
            // a truncated batch.
            session.removeAttribute(SESSION_ROWS);
        } else {
            List<StudentRow> valid = preview.rows().stream().filter(StudentRow::valid).toList();
            session.setAttribute(SESSION_ROWS, valid);
        }
        model.addAttribute("activeMenu", "import");
        model.addAttribute("preview", preview);
        return "admin/import";
    }

    @PostMapping("/students/apply")
    public String applyStudents(HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<StudentRow> rows = (List<StudentRow>) session.getAttribute(SESSION_ROWS);
        if (rows == null || rows.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Nothing to import — upload and preview a file first.");
            return "redirect:/admin/import";
        }
        List<CreatedCredential> creds = csvImportService.applyStudents(rows);
        session.removeAttribute(SESSION_ROWS);
        session.setAttribute(SESSION_CREDS, creds);
        long created = creds.stream().filter(c -> "created".equals(c.status())).count();
        ra.addFlashAttribute("successMessage", created + " student account(s) created. "
                + "Download the passwords below — they are shown only once.");
        return "redirect:/admin/import/students/results";
    }

    @GetMapping("/students/results")
    public String results(HttpSession session, Model model) {
        @SuppressWarnings("unchecked")
        List<CreatedCredential> creds = (List<CreatedCredential>) session.getAttribute(SESSION_CREDS);
        model.addAttribute("activeMenu", "import");
        model.addAttribute("creds", creds);
        return "admin/import";
    }

    @GetMapping("/students/results.csv")
    public ResponseEntity<String> resultsCsv(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<CreatedCredential> creds = (List<CreatedCredential>) session.getAttribute(SESSION_CREDS);
        if (creds == null || creds.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // One-shot: the plaintext passwords are handed out exactly once, then dropped.
        session.removeAttribute(SESSION_CREDS);
        return csvDownload(csvImportService.credentialsCsv(creds), "unisubmit-credentials.csv");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Academic structure — faculties, departments, programmes, units, curriculum,
    // teaching assignments, enrolments. One set of handlers for all seven kinds;
    // the {kind} path variable selects the columns and the applier.
    // ════════════════════════════════════════════════════════════════════════════

    private static final String SESSION_ACADEMIC_ROWS = "importAcademicRows";
    private static final String SESSION_ACADEMIC_KIND = "importAcademicKind";

    @GetMapping("/academic/{kind}/preview")
    public String academicPreviewGet() {
        return "redirect:/admin/import";
    }

    @PostMapping("/academic/{kind}/preview")
    public String previewAcademic(@PathVariable AcademicImportService.Kind kind,
                                  @RequestParam("file") MultipartFile file,
                                  HttpSession session, Model model, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Choose a file first.");
            return "redirect:/admin/import";
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            ra.addFlashAttribute("errorMessage", "File too large — max 5 MB; split it and import in batches.");
            return "redirect:/admin/import";
        }

        AcademicImportService.AcademicPreview preview = academicImportService.parse(kind, file);
        // Same rule as the student importer: a fatal preview must never leave applicable
        // rows in the session, or a direct POST to apply could import a truncated batch.
        if (preview.fatalError() != null) {
            session.removeAttribute(SESSION_ACADEMIC_ROWS);
            session.removeAttribute(SESSION_ACADEMIC_KIND);
        } else {
            session.setAttribute(SESSION_ACADEMIC_ROWS,
                    preview.rows().stream().filter(AcademicImportService.AcademicRow::valid).toList());
            session.setAttribute(SESSION_ACADEMIC_KIND, kind);
        }
        model.addAttribute("activeMenu", "import");
        model.addAttribute("academicPreview", preview);
        model.addAttribute("academicKind", kind);
        model.addAttribute("academicKinds", AcademicImportService.Kind.values());
        return "admin/import";
    }

    @PostMapping("/academic/apply")
    public String applyAcademic(HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<AcademicImportService.AcademicRow> rows =
                (List<AcademicImportService.AcademicRow>) session.getAttribute(SESSION_ACADEMIC_ROWS);
        AcademicImportService.Kind kind =
                (AcademicImportService.Kind) session.getAttribute(SESSION_ACADEMIC_KIND);

        if (rows == null || rows.isEmpty() || kind == null) {
            ra.addFlashAttribute("errorMessage", "Nothing to import — upload and preview a file first.");
            return "redirect:/admin/import";
        }

        AcademicImportService.ApplyResult result = academicImportService.apply(kind, rows);
        session.removeAttribute(SESSION_ACADEMIC_ROWS);
        session.removeAttribute(SESSION_ACADEMIC_KIND);

        String summary = result.created() + " created, " + result.skipped()
                + " already existed" + (result.failed() > 0 ? ", " + result.failed() + " failed" : "") + ".";
        if (result.failed() > 0) {
            ra.addFlashAttribute("errorMessage", kind.getLabel() + ": " + summary + " "
                    + String.join(" · ", result.messages()));
        } else {
            ra.addFlashAttribute("successMessage", kind.getLabel() + ": " + summary);
        }
        return "redirect:/admin/import";
    }

    @GetMapping("/template/academic/{kind}.csv")
    public ResponseEntity<String> templateAcademic(@PathVariable AcademicImportService.Kind kind) {
        return csvDownload(kind.templateCsv(),
                "unisubmit-" + kind.name().toLowerCase() + "-template.csv");
    }

    @GetMapping("/template/students.csv")
    public ResponseEntity<String> templateStudents() {
        return csvDownload(csvImportService.studentTemplateCsv(), "students-template.csv");
    }

    // ── Lecturers — same preview → apply → one-shot credentials flow ────────────

    @GetMapping("/lecturers/preview")
    public String lecturerPreviewGet() {
        return "redirect:/admin/import";
    }

    @PostMapping("/lecturers/preview")
    public String previewLecturers(@RequestParam("file") MultipartFile file,
                                   HttpSession session, Model model, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Choose a CSV file first.");
            return "redirect:/admin/import";
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            ra.addFlashAttribute("errorMessage", "File too large — max 5 MB; split it and import in batches.");
            return "redirect:/admin/import";
        }
        CsvImportService.LecturerPreview preview = csvImportService.parseLecturers(file);
        if (preview.fatalError() != null) {
            session.removeAttribute(SESSION_LECTURER_ROWS);
        } else {
            session.setAttribute(SESSION_LECTURER_ROWS,
                    preview.rows().stream().filter(CsvImportService.LecturerRow::valid).toList());
        }
        model.addAttribute("activeMenu", "import");
        model.addAttribute("lecturerPreview", preview);
        return "admin/import";
    }

    @PostMapping("/lecturers/apply")
    public String applyLecturers(HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<CsvImportService.LecturerRow> rows =
                (List<CsvImportService.LecturerRow>) session.getAttribute(SESSION_LECTURER_ROWS);
        if (rows == null || rows.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Nothing to import — upload and preview a file first.");
            return "redirect:/admin/import";
        }
        List<CreatedCredential> creds = csvImportService.applyLecturers(rows);
        session.removeAttribute(SESSION_LECTURER_ROWS);
        session.setAttribute(SESSION_LECTURER_CREDS, creds);
        long created = creds.stream().filter(c -> "created".equals(c.status())).count();
        ra.addFlashAttribute("successMessage", created + " lecturer account(s) created. "
                + "Download the passwords below — they are shown only once.");
        return "redirect:/admin/import/lecturers/results";
    }

    @GetMapping("/lecturers/results")
    public String lecturerResults(HttpSession session, Model model) {
        @SuppressWarnings("unchecked")
        List<CreatedCredential> creds =
                (List<CreatedCredential>) session.getAttribute(SESSION_LECTURER_CREDS);
        model.addAttribute("activeMenu", "import");
        model.addAttribute("lecturerCreds", creds);
        return "admin/import";
    }

    @GetMapping("/lecturers/results.csv")
    public ResponseEntity<String> lecturerResultsCsv(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<CreatedCredential> creds =
                (List<CreatedCredential>) session.getAttribute(SESSION_LECTURER_CREDS);
        if (creds == null || creds.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        session.removeAttribute(SESSION_LECTURER_CREDS);
        return csvDownload(csvImportService.lecturerCredentialsCsv(creds), "unisubmit-lecturer-credentials.csv");
    }

    @GetMapping("/template/lecturers.csv")
    public ResponseEntity<String> templateLecturers() {
        return csvDownload(csvImportService.lecturerTemplateCsv(), "lecturers-template.csv");
    }

    private ResponseEntity<String> csvDownload(String body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
