package com.smartbudget.pattern.observer;

/**
 * Something that wants to hear about budget threshold crossings.
 *
 * <p>Kept to a single method so it can be implemented as a lambda in tests and
 * by any UI component without ceremony.
 */
@FunctionalInterface
public interface BudgetListener {

    void onBudgetAlert(BudgetAlert alert);
}
