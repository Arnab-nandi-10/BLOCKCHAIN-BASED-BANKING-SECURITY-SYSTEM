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
 * <p>Account numbers follow the banking industry standard of 10–12 digit numeric
 * identifiers (compatible with ACH routing, SWIFT BBAN, and most core-banking
 * account schemas).
 *
 * @param fromAccount account originating the transaction (10–12 digits)
 * @param toAccount   destination account (10–12 digits)
 * @param amount      transaction amount; must be positive
 * @param currency    ISO 4217 three-letter currency code (e.g. "USD")
 * @param type        transaction category
 * @param metadata    optional arbitrary key-value metadata attached to the transaction
 */
public record SubmitTransactionRequest(

        @NotBlank(message = "fromAccount must not be blank")
        @Pattern(
            regexp = "^[0-9]{10,12}$",
            message = "fromAccount must be a 10–12 digit numeric account number"
        )
        String fromAccount,

        @NotBlank(message = "toAccount must not be blank")
        @Pattern(
            regexp = "^[0-9]{10,12}$",
            message = "toAccount must be a 10–12 digit numeric account number"
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

