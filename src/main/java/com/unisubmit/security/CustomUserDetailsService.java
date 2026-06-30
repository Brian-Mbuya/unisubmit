package com.unisubmit.security;

import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.UserRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                    StudentProfileRepository studentProfileRepository,
                                    LecturerProfileRepository lecturerProfileRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? "" : identifier.trim();

        // Prefer username first so self-registered accounts are not shadowed by
        // seeded/demo IDs that happen to share the same identifier string.
        Optional<User> userOpt = userRepository.findByUsername(normalized);
        
        if (userOpt.isEmpty()) {
            Optional<StudentProfile> sp = studentProfileRepository.findByAdmissionNumber(normalized);
            if (sp.isPresent()) {
                userOpt = Optional.of(sp.get().getUser());
            } else {
                Optional<LecturerProfile> lp = lecturerProfileRepository.findByStaffNumber(normalized);
                if (lp.isPresent()) {
                    userOpt = Optional.of(lp.get().getUser());
                }
            }
        }

        User user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        // Soft-deleted accounts behave as if they no longer exist.
        if (user.isDeleted()) {
            throw new UsernameNotFoundException("User not found: " + identifier);
        }

        // Suspended accounts are rejected with the reason surfaced to the login page.
        if (user.isSuspended()) {
            String reason = user.getSuspendedReason() == null ? "No reason provided" : user.getSuspendedReason();
            throw new LockedException("Your account has been suspended: " + reason);
        }

        return new CustomUserDetails(user);
    }
}
