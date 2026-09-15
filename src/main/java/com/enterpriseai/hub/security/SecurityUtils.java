package com.enterpriseai.hub.security;

import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static AppUserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "No authenticated user in context"));
    }

    public static Long currentUserId() {
        return requirePrincipal().getId();
    }
}
