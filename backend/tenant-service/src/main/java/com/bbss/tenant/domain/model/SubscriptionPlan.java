package com.bbss.tenant.domain.model;

public enum SubscriptionPlan {

    STARTER(5000, 10),
    PROFESSIONAL(50000, 50),
    ENTERPRISE(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int monthlyTransactionLimit;
    private final int maxUsers;

    SubscriptionPlan(int monthlyTransactionLimit, int maxUsers) {
        this.monthlyTransactionLimit = monthlyTransactionLimit;
        this.maxUsers = maxUsers;
    }

    public int getMonthlyTransactionLimit() {
        return monthlyTransactionLimit;
    }

    public int getMaxUsers() {
        return maxUsers;
    }
}
