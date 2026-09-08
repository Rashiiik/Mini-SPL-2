package com.smartbudget.service;

import com.smartbudget.model.Budget;
import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Budget targets, spend tracking, and the threshold rule that drives alerts.
 */
public class BudgetService {

    private final BudgetDao budgetDao;
    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;
    private final BudgetEventBus eventBus;

    public BudgetService(BudgetDao budgetDao, TransactionDao transactionDao,
                         CategoryDao categoryDao, BudgetEventBus eventBus) {
        this.budgetDao = budgetDao;
        this.transactionDao = transactionDao;
        this.categoryDao = categoryDao;
        this.eventBus = eventBus;
    }

    public Budget setTarget(int categoryId, YearMonth month, double targetAmount) {
        if (month == null) {
            throw new ValidationException("Please choose a month for this budget.");
        }
        if (targetAmount <= 0) {
            throw new ValidationException("A budget target must be greater than zero.");
        }
        if (categoryDao.findById(categoryId).isEmpty()) {
            throw new ValidationException("That category no longer exists.");
        }
        return budgetDao.save(new Budget(null, categoryId, month, targetAmount));
    }

    public void removeTarget(int budgetId) {
        budgetDao.delete(budgetId);
    }

    public List<Budget> targetsFor(YearMonth month) {
        return budgetDao.findByMonth(month);
    }

    public Optional<Budget> targetFor(int categoryId, YearMonth month) {
        return budgetDao.findByCategoryAndMonth(categoryId, month);
    }

    /** Total spent in a category that month, as a positive amount. */
    public double spentIn(int categoryId, YearMonth month) {
        return transactionDao.sumExpensesByCategoryAndMonth(categoryId, month);
    }

    /**
     * Fraction of the target used. Returns {@code 0} when no budget is set, so
     * callers can display a bar for every category without null-checking.
     */
    public double utilisation(int categoryId, YearMonth month) {
        Optional<Budget> budget = budgetDao.findByCategoryAndMonth(categoryId, month);
        if (budget.isEmpty() || budget.get().getTargetAmount() <= 0) {
            return 0;
        }
        return spentIn(categoryId, month) / budget.get().getTargetAmount();
    }

    /**
     * Decides whether a just-saved transaction crossed a threshold, and publishes
     * an alert if so.
     *
     * <p>The severity is computed twice — once for the spend excluding this
     * transaction, once including it — and an alert is published only when the
     * severity got worse. That is what makes the alert fire on the crossing
     * itself rather than on every later purchase in an already-blown category,
     * which would train the user to ignore it.
     */
    public Optional<BudgetAlert> evaluateAfter(Transaction transaction) {
        if (transaction == null || transaction.getCategoryId() == null || !transaction.isExpense()) {
            return Optional.empty();
        }

        YearMonth month = YearMonth.from(transaction.getDate());
        Optional<Budget> budget = budgetDao.findByCategoryAndMonth(transaction.getCategoryId(), month);
        if (budget.isEmpty() || budget.get().getTargetAmount() <= 0) {
            return Optional.empty();
        }

        double target = budget.get().getTargetAmount();
        double spentAfter = spentIn(transaction.getCategoryId(), month);
        double spentBefore = spentAfter - transaction.getAbsoluteAmount();

        AlertSeverity before = AlertSeverity.forUtilisation(spentBefore / target);
        AlertSeverity after = AlertSeverity.forUtilisation(spentAfter / target);
        if (!after.isWorseThan(before)) {
            return Optional.empty();
        }

        String categoryName = categoryDao.findById(transaction.getCategoryId())
                .map(Category::getName)
                .orElse("Uncategorised");

        BudgetAlert alert = new BudgetAlert(categoryName, month, spentAfter, target, after);
        eventBus.publish(alert);
        return Optional.of(alert);
    }

    /**
     * Current standing of every category that has a budget this month, for the
     * dashboard and budget screens. One of the required analytical operations.
     */
    public List<BudgetAlert> statusFor(YearMonth month) {
        return budgetDao.findByMonth(month).stream()
                .map(budget -> {
                    double spent = spentIn(budget.getCategoryId(), month);
                    String name = categoryDao.findById(budget.getCategoryId())
                            .map(Category::getName)
                            .orElse("Uncategorised");
                    return new BudgetAlert(name, month, spent, budget.getTargetAmount(),
                            AlertSeverity.forUtilisation(spent / budget.getTargetAmount()));
                })
                .toList();
    }
}
