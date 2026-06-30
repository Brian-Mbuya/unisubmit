package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Department;
import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.NotificationType;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.DepartmentRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       LecturerProfileRepository lecturerProfileRepository,
                       DepartmentRepository departmentRepository,
                       CourseRepository courseRepository,
                       PasswordEncoder passwordEncoder,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public User createUser(String username, String password, String name, Role role,
                           String studentId, String staffId,
                           Long departmentId, Long courseId,
                           Integer yearOfStudy, Integer semesterNumber) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new com.unisubmit.exception.DuplicateEntityException("Username already exists.");
        }
        
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name.trim());
        user.setRole(role);
        user = userRepository.save(user);

        if (role == Role.STUDENT && studentId != null && !studentId.isBlank()) {
            if (studentProfileRepository.findByAdmissionNumber(studentId.trim()).isPresent()) {
                throw new com.unisubmit.exception.DuplicateEntityException("Student ID already exists.");
            }
            StudentProfile profile = new StudentProfile();
            profile.setUser(user);
            profile.setAdmissionNumber(studentId.trim());
            if (courseId != null) {
                Course course = courseRepository.findById(courseId).orElse(null);
                profile.setProgramme(course);
            }
            profile.setCurrentYear(yearOfStudy);
            profile.setCurrentSemester(semesterNumber);
            studentProfileRepository.save(profile);
            user.setStudentProfile(profile);
        } else if (role == Role.LECTURER && staffId != null && !staffId.isBlank()) {
            if (lecturerProfileRepository.findByStaffNumber(staffId.trim()).isPresent()) {
                throw new com.unisubmit.exception.DuplicateEntityException("Staff ID already exists.");
            }
            LecturerProfile profile = new LecturerProfile();
            profile.setUser(user);
            profile.setStaffNumber(staffId.trim());
            if (departmentId != null) {
                Department dept = departmentRepository.findById(departmentId).orElse(null);
                profile.setDepartment(dept);
            }
            lecturerProfileRepository.save(profile);
            user.setLecturerProfile(profile);
        }

        return user;
    }

    /** Legacy fallback wrapper */
    public User createUser(String username, String password, String name, Role role, String studentId, String staffId) {
        return createUser(username, password, name, role, studentId, staffId, null, null, null, null);
    }

    public Optional<User> findByUsername(String username) { return userRepository.findByUsername(username); }
    public Optional<User> findById(Long id) { return userRepository.findById(id); }

    public List<User> findAll() { return userRepository.findByDeletedFalse(); }
    public List<User> findByRole(Role role) { return userRepository.findByRoleAndDeletedFalse(role); }

    public long countAll() { return userRepository.countByDeletedFalse(); }
    public long countByRole(Role r) { return userRepository.countByRoleAndDeletedFalse(r); }

    public List<User> searchStudents(String q) {
        if (q == null || q.isBlank()) return List.of();
        return userRepository.searchStudents(q.trim(), PageRequest.of(0, 8));
    }

    @Transactional
    public void deleteUser(Long userId, String currentUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.unisubmit.exception.SubmissionNotFoundException("User not found: " + userId));

        if (user.getUsername().equals(currentUsername)) {
            throw new RuntimeException("You cannot delete the account you are currently signed in with");
        }

        String suffix = "_del_" + userId;
        user.setUsername(user.getUsername() + suffix);
        
        if (user.getStudentProfile() != null) {
            StudentProfile sp = user.getStudentProfile();
            sp.setAdmissionNumber(sp.getAdmissionNumber() + suffix);
            studentProfileRepository.save(sp);
        }
        
        if (user.getLecturerProfile() != null) {
            LecturerProfile lp = user.getLecturerProfile();
            lp.setStaffNumber(lp.getStaffNumber() + suffix);
            lecturerProfileRepository.save(lp);
        }

        user.setDeleted(true);
        userRepository.save(user);
    }

    @Transactional
    public void suspendUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.unisubmit.exception.SubmissionNotFoundException("User not found: " + userId));
        user.setSuspended(true);
        user.setSuspendedReason(reason == null || reason.isBlank() ? "No reason provided" : reason.trim());
        userRepository.save(user);
        notificationService.createNotification(user, NotificationType.SYSTEM_NOTICE,
                "Your account has been suspended: " + user.getSuspendedReason(), null);
    }

    @Transactional
    public void unsuspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.unisubmit.exception.SubmissionNotFoundException("User not found: " + userId));
        user.setSuspended(false);
        user.setSuspendedReason(null);
        userRepository.save(user);
    }
}
