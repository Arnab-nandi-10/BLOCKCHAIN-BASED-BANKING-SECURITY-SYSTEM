package com.bbss.transaction.dto;

import com.bbss.transaction.domain.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request payload for submitting a new transaction.
 *
 * <p>Account identifiers vary across banks and core systems. Some tenants use
 * numeric accounts, others use short internal aliases, and some use
 * alphanumeric IDs with separators. The API therefore validates a general
 * account identifier contract instead of hard-coding a single numeric format.
 *
 * @param fromAccount account originating the transaction
 * @param toAccount   destination account
 * @param amount      transaction amount; must be positive
 * @param currency    ISO 4217 three-letter currency code (e.g. "USD")
 * @param type        transaction category
 * @param metadata    optional arbitrary key-value metadata attached to the transaction
 */
public record SubmitTransactionRequest(

        @NotBlank(message = "fromAccount must not be blank")
        @Size(min = 4, max = 34, message = "fromAccount must be between 4 and 34 characters")
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9-]{3,33}$",
            message = "fromAccount may contain letters, numbers, and hyphens only"
        )
        String fromAccount,

        @NotBlank(message = "toAccount must not be blank")
        @Size(min = 4, max = 34, message = "toAccount must be between 4 and 34 characters")
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9-]{3,33}$",
            message = "toAccount may contain letters, numbers, and hyphens only"
        )
        String toAccount,

        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency must not be blank")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 characters (ISO 4217)")
        String currency,

        @NotNull(message = "type must not be null")
        TransactionType type,

        Map<String, String> metadata
) {}
