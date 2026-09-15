package com.enterpriseai.hub.mapper;

import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.user.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Entity to DTO translation. Hand-written rather than generated: the mappings are small,
 * explicit, and there is nowhere for an entity to leak into the API by accident.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganization(),
                user.getJobTitle(),
                user.isEnabled(),
                user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
