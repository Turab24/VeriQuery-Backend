package com.enterpriseai.hub.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

@Schema(name = "UpdateUserRequest", description = "Administrative update of a user account")
public record UpdateUserRequest(

        Boolean enabled,

        @Schema(example = "[\"ROLE_USER\"]", description = "Complete replacement set of roles")
        @NotEmpty(message = "At least one role must be assigned")
        Set<String> roles) {
}
