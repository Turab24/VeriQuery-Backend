package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.AuditLog;
import com.enterpriseai.hub.observability.RequestContext;
import com.enterpriseai.hub.repository.AuditLogRepository;
import com.enterpriseai.hub.security.AppUserPrincipal;
import com.enterpriseai.hub.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Writes the audit trail. Runs in its own transaction so an audit failure can never roll
 * back the business operation it is describing, and so the record survives even when the
 * caller's transaction is later rolled back for an unrelated reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, Object resourceId, String details) {
        try {
            Optional<AppUserPrincipal> principal = SecurityUtils.currentPrincipal();

            AuditLog entry = new AuditLog();
            entry.setUserId(principal.map(AppUserPrincipal::getId).orElse(null));
            entry.setActorEmail(principal.map(AppUserPrincipal::getEmail).orElse("system"));
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
            entry.setDetails(truncate(details));
            entry.setRequestId(RequestContext.currentRequestId());

            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to write audit entry for action {}: {}", action, ex.getMessage());
        }
    }

    private String truncate(String details) {
        if (details == null) {
            return null;
        }
        return details.length() <= 1000 ? details : details.substring(0, 997) + "...";
    }
}
