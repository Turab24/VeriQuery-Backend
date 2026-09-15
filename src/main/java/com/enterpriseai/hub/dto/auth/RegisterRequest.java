package com.enterpriseai.hub.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Self-service account creation")
public record RegisterRequest(

        @Schema(example = "jane.doe@acme-bank.com")
        @NotBlank(message = "E-mail is required")
        @Email(message = "A valid e-mail address is required")
        @Size(max = 180)
        String email,

        @Schema(example = "Str0ng!Passphrase", description = "At least 10 characters with an upper case letter, a lower case letter and a digit")
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 100, message = "Password must be between 10 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain an upper case letter, a lower case letter and a digit")
        String password,

        @Schema(example = "Jane Doe")
        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @Schema(example = "Acme Bank")
        @Size(max = 150)
        String organization,

        @Schema(example = "Integration Engineer")
        @Size(max = 150)
        String jobTitle) {
}
