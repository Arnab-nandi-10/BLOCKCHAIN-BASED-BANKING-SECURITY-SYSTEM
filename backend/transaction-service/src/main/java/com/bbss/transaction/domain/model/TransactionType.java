package com.bbss.transaction.domain.model;

/**
 * Categorises the financial operation being executed.
 */
public enum TransactionType {

    /**
     * Move funds from one account to another within the system.
     */
    TRANSFER,

    /**
     * Payment to an external party or merchant.
     */
    PAYMENT,

    /**
     * Withdrawal of funds from an account.
     */
    WITHDRAWAL,

    /**
     * Deposit of funds into an account.
     */
    DEPOSIT
}
