package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop20ByOrderByCreatedAtDesc();

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
