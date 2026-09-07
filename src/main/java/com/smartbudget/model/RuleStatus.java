package com.smartbudget.model;

/**
 * Lifecycle of a recurring rule as the user reviews it.
 *
 * <p>{@code FLAGGED} is set by subscription-creep detection; the user then moves
 * it to {@code ACTIVE}, {@code IGNORED}, or {@code CANCELLED} in the review screen.
 */
public enum RuleStatus {
    ACTIVE("active"),
    FLAGGED("flagged"),
    IGNORED("ignored"),
    CANCELLED("cancelled");

    private final String dbValue;

    RuleStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RuleStatus fromDb(String value) {
        for (RuleStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown rule status: " + value);
    }
}
