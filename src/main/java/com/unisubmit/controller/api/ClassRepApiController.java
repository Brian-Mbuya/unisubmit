package com.unisubmit.controller.api;

import com.unisubmit.domain.Curriculum;
import com.unisubmit.service.ClassRepService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Class-representative actions, on the mobile chain because reps are students and
 * students live in the Flutter app.
 * <p>
 * Every method is gated twice: {@code @PreAuthorize("hasAuthority('CLASS_REP')")} rejects
 * non-reps cheaply, and {@link ClassRepService} re-reads the profile from the database to
 * decide what class the caller may touch. The annotation alone is not enough — the
 * authority is baked into a JWT that stays valid until it expires, so a rep demoted
 * mid-token would still pass the annotation. The service check is the one that counts.
 */
@RestController
@RequestMapping("/api/v1/student/class-rep")
@PreAuthorize("hasAuthority('CLASS_REP')")
public class ClassRepApiController {

    private final ClassRepService classRepService;
    private final CurrentUser currentUser;

    public ClassRepApiController(ClassRepService classRepService, CurrentUser currentUser) {
        this.classRepService = classRepService;
        this.currentUser = currentUser;
    }

    public record UnitView(Long unitId, String code, String name, Long curriculumId) {}

    public record ClassmateView(Long studentProfileId, String admissionNumber, String name) {}

    public record EnrolRequest(@NotNull Long studentProfileId, @NotNull Long unitId) {}

    public record AssignLecturerRequest(@NotNull Long lecturerProfileId, @NotNull Long unitId) {}

    public record NoticeRequest(@NotNull Long unitId, @NotBlank String title, @NotBlank String message) {}

    public record ActionResult(String message) {}

    /** The units the rep may act on — the mobile app uses this to populate its pickers. */
    @GetMapping("/units")
    public List<UnitView> myUnits() {
        return classRepService.myClassUnits(currentUser.require()).stream()
                .map(ClassRepApiController::toUnitView)
                .toList();
    }

    @GetMapping("/classmates")
    public List<ClassmateView> myClassmates() {
        return classRepService.myClassmates(currentUser.require()).stream()
                .map(p -> new ClassmateView(p.getId(), p.getAdmissionNumber(), p.getUser().getName()))
                .toList();
    }

    @PostMapping("/enrol")
    public ResponseEntity<ActionResult> enrol(@Valid @RequestBody EnrolRequest request) {
        String message = classRepService.enrolClassmate(
                currentUser.require(), request.studentProfileId(), request.unitId());
        return ResponseEntity.ok(new ActionResult(message));
    }

    @PostMapping("/assign-lecturer")
    public ResponseEntity<ActionResult> assignLecturer(@Valid @RequestBody AssignLecturerRequest request) {
        String message = classRepService.assignLecturer(
                currentUser.require(), request.lecturerProfileId(), request.unitId());
        return ResponseEntity.ok(new ActionResult(message));
    }

    @PostMapping("/notice")
    public ResponseEntity<ActionResult> postNotice(@Valid @RequestBody NoticeRequest request) {
        classRepService.postClassNotice(currentUser.require(), request.unitId(),
                request.title(), request.message());
        return ResponseEntity.ok(new ActionResult("Notice posted to your class."));
    }

    private static UnitView toUnitView(Curriculum c) {
        return new UnitView(c.getUnit().getId(), c.getUnit().getUnitCode(),
                c.getUnit().getUnitName(), c.getId());
    }
}
