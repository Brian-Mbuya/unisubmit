package com.unisubmit.security;

import com.unisubmit.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final boolean classRep;

    public CustomUserDetails(User user) {
        this(user, false);
    }

    /**
     * The class-rep flag is captured HERE rather than read from
     * {@code user.getStudentProfile().isClassRep()} on demand, because the profile
     * association is LAZY. Authorities are read on every request — including on the
     * stateless API chain where no Hibernate session is open — so a lazy hop would
     * throw LazyInitializationException. CustomUserDetailsService resolves it once,
     * inside the session, and passes it in.
     */
    public CustomUserDetails(User user, boolean classRep) {
        this.user = user;
        this.classRep = classRep;
    }

    public User getUser() {
        return user;
    }

    public boolean isClassRep() {
        return classRep;
    }

    /**
     * ROLE_&lt;role&gt; always, plus a bare {@code CLASS_REP} authority when this student is
     * their class's representative. Deliberately NOT a role: a rep is still a student, so
     * the extra authority is additive and every existing {@code hasRole('STUDENT')} check
     * keeps working. Guard rep-only actions with {@code hasAuthority('CLASS_REP')}.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(2);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (classRep) {
            authorities.add(new SimpleGrantedAuthority("CLASS_REP"));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
