package com.unisubmit.config;

import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repairs accounts that were created without a role profile — the cause of
 * "can't save lecturer details" (a lecturer with no LecturerProfile has no id
 * for the admin edit form to target). Runs every boot but only touches users
 * that are actually missing a profile, so it is idempotent and cheap.
 */
@Component
@Order(5)
public class ProfileBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileBackfillRunner.class);

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;

    public ProfileBackfillRunner(UserRepository userRepository,
                                 StudentProfileRepository studentProfileRepository,
                                 LecturerProfileRepository lecturerProfileRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int repaired = 0;
        for (User user : userRepository.findAll()) {
            if (user.isDeleted()) {
                continue;
            }
            if (user.getRole() == Role.LECTURER && user.getLecturerProfile() == null) {
                LecturerProfile profile = new LecturerProfile();
                profile.setUser(user);
                profile.setStaffNumber(uniqueStaff("STAFF-" + user.getId()));
                lecturerProfileRepository.save(profile);
                user.setLecturerProfile(profile);
                userRepository.save(user);
                repaired++;
            } else if (user.getRole() == Role.STUDENT && user.getStudentProfile() == null) {
                StudentProfile profile = new StudentProfile();
                profile.setUser(user);
                profile.setAdmissionNumber(uniqueAdmission("STU-" + user.getId()));
                studentProfileRepository.save(profile);
                user.setStudentProfile(profile);
                userRepository.save(user);
                repaired++;
            }
        }
        if (repaired > 0) {
            log.info("Backfilled {} missing student/lecturer profile(s) so they are editable.", repaired);
        }
    }

    private String uniqueStaff(String base) {
        String candidate = base;
        int n = 1;
        while (lecturerProfileRepository.findByStaffNumberIgnoreCase(candidate).isPresent()) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private String uniqueAdmission(String base) {
        String candidate = base;
        int n = 1;
        while (studentProfileRepository.findByAdmissionNumberIgnoreCase(candidate).isPresent()) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }
}
