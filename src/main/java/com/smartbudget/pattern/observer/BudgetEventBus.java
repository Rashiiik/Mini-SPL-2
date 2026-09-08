package com.smartbudget.pattern.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * The subject half of the Observer pattern for budget alerts.
 *
 * <p><b>Problem it solves:</b> saving a transaction has to notify whatever
 * currently cares about overspending — today the dashboard banner, later a
 * notification tray or an email digest. Calling those directly from
 * {@code BudgetService} would mean editing budget logic every time a new
 * consumer appears, and would make the service impossible to unit test without
 * constructing UI objects.
 *
 * <p><b>Why Observer:</b> publishers and subscribers vary independently. The
 * budget rules answer "has a threshold been crossed"; who reacts is not their
 * concern. Adding a listener later touches no existing class.
 *
 * <p><b>Alternative considered:</b> passing a callback into each
 * {@code BudgetService} call. That works for one consumer but degrades quickly
 * once several components need the same event, and it pushes the fan-out
 * responsibility onto every caller.
 */
public class BudgetEventBus {

    private final List<BudgetListener> listeners = new ArrayList<>();

    public void subscribe(BudgetListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(BudgetListener listener) {
        listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    /**
     * Delivers an alert to every subscriber.
     *
     * <p>Iterates over a snapshot so a listener may unsubscribe itself while
     * being notified without triggering a {@link java.util.ConcurrentModificationException}
     * — a screen that closes in response to an alert would otherwise do exactly that.
     */
    public void publish(BudgetAlert alert) {
        for (BudgetListener listener : new ArrayList<>(listeners)) {
            listener.onBudgetAlert(alert);
        }
    }
}
