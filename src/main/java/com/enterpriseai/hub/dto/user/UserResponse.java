package com.enterpriseai.hub.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "User")
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String organization,
        String jobTitle,
        boolean enabled,
        List<String> roles,
        Instant createdAt,
        Instant lastLoginAt) {
}
