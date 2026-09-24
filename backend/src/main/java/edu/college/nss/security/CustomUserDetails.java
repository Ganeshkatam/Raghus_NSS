package edu.college.nss.security;

import edu.college.nss.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String name;
    private final String email;
    private final String password;
    private final String status;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getUserId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.status = user.getStatus();

        Set<SimpleGrantedAuthority> auths = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                String name = role.getName();
                String cleanName = name.startsWith("ROLE_") ? name.substring(5) : name;
                auths.add(new SimpleGrantedAuthority(cleanName));
                auths.add(new SimpleGrantedAuthority("ROLE_" + cleanName));
                if (role.getPermissions() != null) {
                    role.getPermissions().forEach(perm ->
                        auths.add(new SimpleGrantedAuthority(perm.getName()))
                    );
                }
            });
        }
        this.authorities = auths;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"SUSPENDED".equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
