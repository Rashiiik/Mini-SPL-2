package com.smartbudget.model;

/** Whether a category represents money going out or coming in. */
public enum CategoryType {
    EXPENSE("expense"),
    INCOME("income");

    private final String dbValue;

    CategoryType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static CategoryType fromDb(String value) {
        for (CategoryType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown category type: " + value);
    }
}
