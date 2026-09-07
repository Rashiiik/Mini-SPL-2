package com.smartbudget.model;

/**
 * What the user did with an AI-generated suggestion.
 *
 * <p>Recording this is what keeps the AI advisory rather than authoritative:
 * nothing the model produces is treated as fact until a human accepts it.
 */
public enum UserAction {
    PENDING("pending"),
    ACCEPTED("accepted"),
    EDITED("edited"),
    REJECTED("rejected");

    private final String dbValue;

    UserAction(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static UserAction fromDb(String value) {
        for (UserAction action : values()) {
            if (action.dbValue.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown user action: " + value);
    }
}
