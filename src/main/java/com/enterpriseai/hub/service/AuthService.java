package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.RefreshToken;
import com.enterpriseai.hub.domain.Role;
import com.enterpriseai.hub.domain.RoleName;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.auth.AuthResponse;
import com.enterpriseai.hub.dto.auth.LoginRequest;
import com.enterpriseai.hub.dto.auth.RefreshTokenRequest;
import com.enterpriseai.hub.dto.auth.RegisterRequest;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ConflictException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.mapper.UserMapper;
import com.enterpriseai.hub.repository.RoleRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.AppUserPrincipal;
import com.enterpriseai.hub.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Registration, sign-in and token refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    /**
     * Self-service registration always grants {@code ROLE_USER}. Administrative rights are
     * only ever granted by an existing administrator, never by the registration endpoint.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ConflictException.emailTaken(email);
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER is missing. The V2 seed migration must run before registration is possible."));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().strip());
        user.setOrganization(blankToNull(request.organization()));
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setEnabled(true);
        user.addRole(userRole);

        User saved = userRepository.save(user);
        log.info("Registered new account {} (id={})", saved.getEmail(), saved.getId());
        auditService.record("USER_REGISTERED", "User", saved.getId(), "Self-service registration");

        return issueTokens(saved, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        if (!principal.isEnabled()) {
            throw new DisabledException("Account disabled");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ResourceNotFoundException.user(email));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User {} signed in", user.getEmail());
        auditService.record("USER_LOGIN", "User", user.getId(), null);
        return issueTokens(user, userAgent);
    }

    /**
     * Rotating refresh. The presented token must verify as a JWT of type {@code refresh}
     * and match an active server-side record; both checks must pass.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, String userAgent) {
        Claims claims = jwtService.parseRefreshToken(request.refreshToken())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN,
                        "The refresh token is invalid or has expired"));

        RefreshToken consumed = refreshTokenService.consume(request.refreshToken());
        User user = consumed.getUser();

        if (!user.getEmail().equalsIgnoreCase(claims.getSubject())) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "The refresh token does not match its subject");
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
        }

        return issueTokens(user, userAgent);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthResponse issueTokens(User user, String userAgent) {
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user, principal, userAgent);
        return AuthResponse.of(accessToken,
                refreshToken,
                jwtService.getAccessTokenTtl().toSeconds(),
                userMapper.toResponse(user));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
