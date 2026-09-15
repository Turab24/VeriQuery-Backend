package com.enterpriseai.hub.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @Schema(example = "admin@enterpriseai.local")
        @NotBlank(message = "E-mail is required")
        @Size(max = 180)
        String email,

        @Schema(example = "Admin@12345")
        @NotBlank(message = "Password is required")
        @Size(max = 100)
        String password) {
}
