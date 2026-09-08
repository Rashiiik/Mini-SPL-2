package com.smartbudget.service;

import com.smartbudget.model.Category;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * The two analytical operations: spending by category, and budget versus actual.
 *
 * <p>Deliberately returns data rather than formatted text. Stage 7 wraps report
 * generation in an AI-insight Decorator, and a service that already decided how
 * things should look would leave the decorator nothing clean to wrap.
 */
public class ReportService {

    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final CategoryDao categoryDao;
    private final BudgetService budgetService;

    public ReportService(TransactionDao transactionDao, BudgetDao budgetDao,
                         CategoryDao categoryDao, BudgetService budgetService) {
        this.transactionDao = transactionDao;
        this.budgetDao = budgetDao;
        this.categoryDao = categoryDao;
        this.budgetService = budgetService;
    }

    /** Category name to total spent, highest first. */
    public Map<String, Double> spendingByCategory(YearMonth month) {
        return transactionDao.spendingByCategory(month);
    }

    /** Every budgeted category with its target, actual spend and severity. */
    public List<BudgetAlert> budgetVsActual(YearMonth month) {
        return budgetService.statusFor(month);
    }

    public double totalSpent(YearMonth month) {
        return spendingByCategory(month).values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double totalIncome(YearMonth month) {
        return transactionDao.findByMonth(month).stream()
                .filter(t -> !t.isExpense())
                .mapToDouble(t -> t.getAmount())
                .sum();
    }

    /** Income minus spending: positive means the month ended ahead. */
    public double netForMonth(YearMonth month) {
        return totalIncome(month) - totalSpent(month);
    }

    /** Categories with no budget set this month, so the Budgets screen can offer them. */
    public List<Category> unbudgetedCategories(YearMonth month) {
        List<Integer> budgeted = budgetDao.findByMonth(month).stream()
                .map(b -> b.getCategoryId())
                .toList();
        return categoryDao.findAll().stream()
                .filter(c -> !budgeted.contains(c.getId()))
                .toList();
    }
}
