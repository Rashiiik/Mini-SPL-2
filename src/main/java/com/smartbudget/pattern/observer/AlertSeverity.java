package com.smartbudget.pattern.observer;

/**
 * How far spending has gone against a budget target.
 *
 * <p>The order of the constants is meaningful: {@link #isWorseThan} relies on
 * the natural enum ordering so the alert only fires when the situation
 * deteriorates, not on every save while already over budget.
 */
public enum AlertSeverity {
    OK,
    WARNING,
    EXCEEDED;

    /** Threshold at which spending is considered close enough to warn about. */
    public static final double WARNING_THRESHOLD = 0.80;

    public static AlertSeverity forUtilisation(double utilisation) {
        if (utilisation > 1.0) {
            return EXCEEDED;
        }
        if (utilisation >= WARNING_THRESHOLD) {
            return WARNING;
        }
        return OK;
    }

    public boolean isWorseThan(AlertSeverity other) {
        return compareTo(other) > 0;
    }
}
