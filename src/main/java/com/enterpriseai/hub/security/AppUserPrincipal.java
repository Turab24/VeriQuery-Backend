package com.enterpriseai.hub.security;

import com.enterpriseai.hub.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Authenticated principal. Carries the database id so services never have to re-query the
 * user table just to scope a query to the caller.
 */
@Getter
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> authorityNames;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(Long id,
                            String email,
                            String fullName,
                            String passwordHash,
                            boolean enabled,
                            Set<String> authorityNames) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorityNames = authorityNames;
        this.authorities = authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public static AppUserPrincipal from(User user) {
        Set<String> authorities = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return new AppUserPrincipal(user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                user.isEnabled(),
                authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
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
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdmin() {
        return authorityNames.contains("ROLE_ADMIN");
    }
}
