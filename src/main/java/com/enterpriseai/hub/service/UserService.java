package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.Role;
import com.enterpriseai.hub.domain.RoleName;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.user.ChangePasswordRequest;
import com.enterpriseai.hub.dto.user.UpdateProfileRequest;
import com.enterpriseai.hub.dto.user.UpdateUserRequest;
import com.enterpriseai.hub.dto.user.UserResponse;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.BadRequestException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.mapper.UserMapper;
import com.enterpriseai.hub.repository.RoleRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        return userMapper.toResponse(loadCurrent());
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Pageable pageable) {
        String term = emptyToNull(search);
        Page<User> users = term == null
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(term, term, pageable);
        return users.map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.user(id));
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = loadCurrent();
        user.setFullName(request.fullName().strip());
        user.setOrganization(emptyToNull(request.organization()));
        user.setJobTitle(emptyToNull(request.jobTitle()));
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Changing a password invalidates every existing session: the old refresh tokens are
     * revoked so a device that was signed in with the previous credentials cannot silently
     * keep its access.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadCurrent();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "The current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must differ from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("Password changed for user {}", user.getId());
        auditService.record("PASSWORD_CHANGED", "User", user.getId(), "All sessions revoked");
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));
        Long currentUserId = SecurityUtils.currentUserId();

        if (request.enabled() != null) {
            if (id.equals(currentUserId) && !request.enabled()) {
                throw new BadRequestException("You cannot disable your own account");
            }
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                refreshTokenService.revokeAllForUser(id);
            }
        }

        Set<Role> roles = new LinkedHashSet<>();
        for (String roleName : request.roles()) {
            RoleName parsed = parseRole(roleName);
            roles.add(roleRepository.findByName(parsed)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + roleName)));
        }
        if (id.equals(currentUserId) && roles.stream().noneMatch(role -> role.getName() == RoleName.ROLE_ADMIN)) {
            throw new BadRequestException("You cannot remove your own administrator role");
        }
        user.setRoles(roles);

        User saved = userRepository.save(user);
        auditService.record("USER_UPDATED", "User", id,
                "enabled=" + saved.isEnabled() + ", roles=" + request.roles());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));
        refreshTokenService.revokeAllForUser(id);
        userRepository.delete(user);
        auditService.record("USER_DELETED", "User", id, user.getEmail());
    }

    private RoleName parseRole(String value) {
        String normalized = value.startsWith("ROLE_") ? value : "ROLE_" + value;
        try {
            return RoleName.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role: " + value);
        }
    }

    private User loadCurrent() {
        Long id = SecurityUtils.currentUserId();
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
