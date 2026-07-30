package com.unisubmit.service;

import com.unisubmit.domain.User;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.DepartmentRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private LecturerProfileRepository lecturerProfileRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(
                userRepository,
                studentProfileRepository,
                lecturerProfileRepository,
                departmentRepository,
                courseRepository,
                passwordEncoder,
                notificationService
        );
    }

    @Test
    void deleteUserBlocksCurrentSignedInAccount() {
        User existing = new User();
        existing.setId(4L);
        existing.setUsername("admin");

        when(userRepository.findById(4L)).thenReturn(Optional.of(existing));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.deleteUser(4L, "admin"));

        assertEquals("You cannot delete the account you are currently signed in with", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUserSoftDeletesOtherAccounts() {
        User target = new User();
        target.setId(9L);
        target.setUsername("student9");

        when(userRepository.findById(9L)).thenReturn(Optional.of(target));

        userService.deleteUser(9L, "admin");

        assertTrue(target.isDeleted());
        verify(userRepository).save(target);
    }
}
