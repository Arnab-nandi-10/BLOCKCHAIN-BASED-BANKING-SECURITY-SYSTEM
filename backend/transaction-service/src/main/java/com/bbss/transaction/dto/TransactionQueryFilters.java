package com.bbss.transaction.dto;

import com.bbss.transaction.domain.model.LedgerStatus;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.model.TransactionType;
import com.bbss.transaction.domain.model.VerificationStatus;

import java.time.LocalDateTime;

public record TransactionQueryFilters(
        String search,
        TransactionStatus status,
        TransactionType type,
        LedgerStatus ledgerStatus,
        VerificationStatus verificationStatus,
        LocalDateTime fromDate,
        LocalDateTime toDate
) {}
