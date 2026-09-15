package com.enterpriseai.hub.dto.auth;

import com.enterpriseai.hub.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Issued token pair plus the authenticated profile")
public record AuthResponse(
        String accessToken,
        String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "1800") long expiresInSeconds,
        UserResponse user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
