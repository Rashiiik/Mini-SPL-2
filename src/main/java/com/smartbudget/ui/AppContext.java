package com.smartbudget.ui;

import com.smartbudget.pattern.factory.AccountFactory;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.strategy.CategorizationStrategy;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;
import com.smartbudget.service.AccountService;
import com.smartbudget.service.BudgetService;
import com.smartbudget.service.ReportService;
import com.smartbudget.service.TransactionService;

/**
 * Builds the object graph once and hands the pieces to the views.
 *
 * <p>Deliberately explicit rather than a dependency-injection framework: the
 * whole wiring of the application is visible in one readable method, and no view
 * ever reaches for a singleton. Swapping the categorisation strategy for the AI
 * one in a later stage is a one-line change here and nowhere else.
 */
public class AppContext {

    private final Database database;

    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final RecurringRuleDao recurringRuleDao;
    private final AIInsightDao aiInsightDao;

    private final BudgetEventBus budgetEventBus;
    private final CategorizationStrategy categorizationStrategy;
    private final AccountFactory accountFactory;

    private final AccountService accountService;
    private final BudgetService budgetService;
    private final TransactionService transactionService;
    private final ReportService reportService;

    public AppContext() {
        this(Database.getInstance());
    }

    public AppContext(Database database) {
        this.database = database;

        this.accountDao = new AccountDao(database);
        this.categoryDao = new CategoryDao(database);
        this.transactionDao = new TransactionDao(database);
        this.budgetDao = new BudgetDao(database);
        this.recurringRuleDao = new RecurringRuleDao(database);
        this.aiInsightDao = new AIInsightDao(database);

        this.budgetEventBus = new BudgetEventBus();
        this.accountFactory = new AccountFactory();

        // The single line that decides how transactions are categorised.
        // Stage 5 replaces this with an AI strategy that falls back to this one.
        this.categorizationStrategy = new RuleBasedCategorizationStrategy(categoryDao, recurringRuleDao);

        this.budgetService = new BudgetService(budgetDao, transactionDao, categoryDao, budgetEventBus);
        this.transactionService =
                new TransactionService(transactionDao, accountDao, categorizationStrategy, budgetService);
        this.accountService = new AccountService(accountDao, transactionDao, accountFactory);
        this.reportService = new ReportService(transactionDao, budgetDao, categoryDao, budgetService);
    }

    public Database database() {
        return database;
    }

    public CategoryDao categoryDao() {
        return categoryDao;
    }

    public RecurringRuleDao recurringRuleDao() {
        return recurringRuleDao;
    }

    public AIInsightDao aiInsightDao() {
        return aiInsightDao;
    }

    public BudgetEventBus budgetEventBus() {
        return budgetEventBus;
    }

    public AccountService accountService() {
        return accountService;
    }

    public BudgetService budgetService() {
        return budgetService;
    }

    public TransactionService transactionService() {
        return transactionService;
    }

    public ReportService reportService() {
        return reportService;
    }

    public void shutdown() {
        database.close();
    }
}
