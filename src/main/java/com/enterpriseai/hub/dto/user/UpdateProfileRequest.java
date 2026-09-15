package com.enterpriseai.hub.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @Size(max = 150)
        String organization,

        @Size(max = 150)
        String jobTitle) {
}
