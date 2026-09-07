package com.smartbudget.model;

/**
 * The three account kinds SmartBudget supports.
 *
 * <p>The distinction is not cosmetic: {@link #CREDIT} inverts the meaning of
 * {@code balance} (it holds outstanding debt, so spending increases it), which
 * is why balance updates are computed per type rather than with one formula.
 */
public enum AccountType {
    CHECKING("checking"),
    SAVINGS("savings"),
    CREDIT("credit");

    private final String dbValue;

    AccountType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static AccountType fromDb(String value) {
        for (AccountType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown account type: " + value);
    }
}
