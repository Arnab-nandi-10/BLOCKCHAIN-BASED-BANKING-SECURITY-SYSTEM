package com.bbss.audit.dto;

import com.bbss.audit.domain.model.AuditStatus;

import java.time.LocalDateTime;

public record AuditQueryFilters(
        String entityType,
        String entityId,
        String action,
        AuditStatus status,
        String verificationStatus,
        String search,
        LocalDateTime fromDate,
        LocalDateTime toDate
) {}
