package com.smartbudget.model;

/** What an AI insight is about. */
public enum InsightKind {
    ANOMALY("anomaly"),
    REPORT("report"),
    SUBSCRIPTION("subscription");

    private final String dbValue;

    InsightKind(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static InsightKind fromDb(String value) {
        for (InsightKind kind : values()) {
            if (kind.dbValue.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown insight kind: " + value);
    }
}
