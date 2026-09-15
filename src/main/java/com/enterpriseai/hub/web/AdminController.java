package com.enterpriseai.hub.web;

import com.enterpriseai.hub.common.PageResponse;
import com.enterpriseai.hub.dto.admin.DashboardStatsResponse;
import com.enterpriseai.hub.dto.user.UpdateUserRequest;
import com.enterpriseai.hub.dto.user.UserResponse;
import com.enterpriseai.hub.service.AdminStatsService;
import com.enterpriseai.hub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration. The URL prefix is already restricted to {@code ROLE_ADMIN} in
 * {@link com.enterpriseai.hub.security.SecurityConfig}; the method level annotation is a
 * deliberate second line of defence so a future change to the URL rules cannot silently
 * expose these operations.
 */
@Tag(name = "Administration", description = "Platform statistics and user management (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final AdminStatsService adminStatsService;
    private final UserService userService;

    @Operation(summary = "Dashboard statistics",
            description = "Live counts from this deployment's own tables, cached for a short window.")
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> stats() {
        return ResponseEntity.ok(adminStatsService.dashboard());
    }

    @Operation(summary = "List user accounts")
    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserResponse>> users(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(PageResponse.of(userService.list(search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @Operation(summary = "Get a user account")
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> user(@PathVariable Long id) {
        return ResponseEntity.ok(userService.get(id));
    }

    @Operation(summary = "Enable, disable or re-role a user",
            description = "Disabling an account also revokes its active sessions.")
    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "Delete a user account")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
