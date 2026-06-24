package com.ecommint.accounthr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
