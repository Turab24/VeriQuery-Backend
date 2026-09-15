package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.Role;
import com.enterpriseai.hub.domain.RoleName;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.auth.AuthResponse;
import com.enterpriseai.hub.dto.auth.RegisterRequest;
import com.enterpriseai.hub.exception.ConflictException;
import com.enterpriseai.hub.mapper.UserMapper;
import com.enterpriseai.hub.repository.RoleRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.AppUserPrincipal;
import com.enterpriseai.hub.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Spy
    private UserMapper userMapper = new UserMapper();
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest() {
        return new RegisterRequest("New.User@Acme.TEST", "Str0ngPassphrase", "New User", "Acme", "Engineer");
    }

    private void stubTokenIssuing() {
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(30));
        when(refreshTokenService.issue(any(), any(), any())).thenReturn("refresh-token");
        when(roleRepository.findByName(RoleName.ROLE_USER))
                .thenReturn(Optional.of(new Role(2L, RoleName.ROLE_USER, "Standard user")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return user;
        });
    }

    @Test
    @DisplayName("registration stores a BCrypt hash, never the raw password")
    void hashesThePassword() {
        stubTokenIssuing();
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        authService.register(validRequest(), "JUnit");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Str0ngPassphrase");
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("Str0ngPassphrase", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("registration normalises the e-mail and grants only ROLE_USER")
    void grantsOnlyTheUserRole() {
        stubTokenIssuing();
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        AuthResponse response = authService.register(validRequest(), "JUnit");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getEmail()).isEqualTo("new.user@acme.test");
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly(RoleName.ROLE_USER);
        assertThat(saved.getValue().hasRole(RoleName.ROLE_ADMIN)).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("registering an existing e-mail fails with a conflict and writes nothing")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("new.user@acme.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest(), "JUnit"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a principal built from a user exposes its authorities to Spring Security")
    void principalCarriesAuthorities() {
        User user = new User();
        user.setId(3L);
        user.setEmail("admin@acme.test");
        user.setFullName("Admin");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.addRole(new Role(1L, RoleName.ROLE_ADMIN, null));

        AppUserPrincipal principal = AppUserPrincipal.from(user);

        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(principal.getUsername()).isEqualTo("admin@acme.test");
    }
}
