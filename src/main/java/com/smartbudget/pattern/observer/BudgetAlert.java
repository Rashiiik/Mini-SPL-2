package com.smartbudget.pattern.observer;

import java.time.YearMonth;

/**
 * What listeners receive when spending crosses a budget threshold.
 *
 * <p>Carries everything needed to render a message, so a listener never has to
 * query the database to react. That is what keeps the UI free of business
 * logic and the budget logic free of UI concerns.
 */
public record BudgetAlert(
        String categoryName,
        YearMonth month,
        double spent,
        double target,
        AlertSeverity severity) {

    public double utilisation() {
        return target <= 0 ? 0 : spent / target;
    }

    public double overspend() {
        return Math.max(0, spent - target);
    }

    /** A ready-to-display sentence, so every listener phrases it the same way. */
    public String message() {
        return switch (severity) {
            case EXCEEDED -> String.format(
                    "%s is over budget for %s: spent %.2f of %.2f (%.0f%% over by %.2f).",
                    categoryName, month, spent, target, (utilisation() - 1) * 100, overspend());
            case WARNING -> String.format(
                    "%s is at %.0f%% of its %s budget: spent %.2f of %.2f.",
                    categoryName, utilisation() * 100, month, spent, target);
            case OK -> String.format("%s is within budget for %s.", categoryName, month);
        };
    }
}
