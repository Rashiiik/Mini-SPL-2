package com.smartbudget.model;

/** How often a recurring rule is expected to fire. */
public enum Frequency {
    WEEKLY("weekly", 7),
    MONTHLY("monthly", 30),
    YEARLY("yearly", 365);

    private final String dbValue;
    private final int approximateDays;

    Frequency(String dbValue, int approximateDays) {
        this.dbValue = dbValue;
        this.approximateDays = approximateDays;
    }

    public String dbValue() {
        return dbValue;
    }

    /** Nominal gap in days, used by Stage 6 cadence detection. */
    public int approximateDays() {
        return approximateDays;
    }

    public static Frequency fromDb(String value) {
        for (Frequency frequency : values()) {
            if (frequency.dbValue.equalsIgnoreCase(value)) {
                return frequency;
            }
        }
        throw new IllegalArgumentException("Unknown frequency: " + value);
    }
}
