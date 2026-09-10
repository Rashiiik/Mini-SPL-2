package com.smartbudget.ui;

import com.smartbudget.ai.AIConfig;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.adapter.GroqAdapter;
import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.pattern.command.CommandHistory;
import com.smartbudget.pattern.command.NaturalLanguageParser;
import com.smartbudget.pattern.factory.AccountFactory;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.strategy.AICategorizationStrategy;
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
import com.smartbudget.service.AnomalyService;
import com.smartbudget.service.BudgetService;
import com.smartbudget.service.DashboardAdvisor;
import com.smartbudget.service.ReceiptService;
import com.smartbudget.service.ReportService;
import com.smartbudget.service.SubscriptionService;
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
    private final AIProvider aiProvider;

    private final AccountService accountService;
    private final BudgetService budgetService;
    private final TransactionService transactionService;
    private final ReportService reportService;
    private final AnomalyService anomalyService;
    private final SubscriptionService subscriptionService;
    private final ReceiptService receiptService;
    private final CommandHistory commandHistory;
    private final NaturalLanguageParser naturalLanguageParser;
    private final DashboardAdvisor dashboardAdvisor;

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

        // The AI provider is chosen once, here. With no API key configured the
        // Null Object is used, so every AI-backed feature quietly takes its
        // rule-based path and the application stays fully usable offline.
        AIConfig aiConfig = AIConfig.load();
        this.aiProvider = aiConfig.hasApiKey() ? new GroqAdapter(aiConfig) : new NullAIProvider();

        // The single line that decides how transactions are categorised. The AI
        // strategy holds the rule-based one as its fallback, so degradation is
        // ordinary composition rather than a special case.
        CategorizationStrategy ruleBased =
                new RuleBasedCategorizationStrategy(categoryDao, recurringRuleDao);
        this.categorizationStrategy =
                new AICategorizationStrategy(aiProvider, categoryDao, ruleBased);

        this.budgetService = new BudgetService(budgetDao, transactionDao, categoryDao, budgetEventBus);
        this.transactionService =
                new TransactionService(transactionDao, accountDao, categorizationStrategy, budgetService);
        this.accountService = new AccountService(accountDao, transactionDao, accountFactory);
        this.reportService = new ReportService(transactionDao, budgetDao, categoryDao, budgetService);
        this.anomalyService =
                new AnomalyService(transactionDao, categoryDao, aiInsightDao, aiProvider);
        this.subscriptionService =
                new SubscriptionService(transactionDao, recurringRuleDao, categoryDao);
        this.receiptService = new ReceiptService(aiProvider);
        this.commandHistory = new CommandHistory();
        this.naturalLanguageParser = new NaturalLanguageParser(aiProvider);
        this.dashboardAdvisor = new DashboardAdvisor(aiProvider, reportService, anomalyService);
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

    public AnomalyService anomalyService() {
        return anomalyService;
    }

    public SubscriptionService subscriptionService() {
        return subscriptionService;
    }

    public ReceiptService receiptService() {
        return receiptService;
    }

    public CommandHistory commandHistory() {
        return commandHistory;
    }

    public NaturalLanguageParser naturalLanguageParser() {
        return naturalLanguageParser;
    }

    public AIProvider aiProvider() {
        return aiProvider;
    }

    public DashboardAdvisor dashboardAdvisor() {
        return dashboardAdvisor;
    }

    public void shutdown() {
        database.close();
    }
}
