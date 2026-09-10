package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Category;
import com.smartbudget.model.CategoryType;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard briefing has to say something useful with no model available,
 * which is exactly the path these tests cover.
 */
class DashboardAdvisorTest {

    /**
     * The seed covers this month and the two before it, and a transaction may not
     * be dated in the future, so this is the nearest month with a clean baseline.
     */
    private static final YearMonth MONTH = YearMonth.now().minusMonths(6);

    private Database database;
    private DashboardAdvisor advisor;
    private BudgetService budgetService;
    private TransactionService transactionService;
    private Account account;
    private int diningCategory;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        AccountDao accountDao = new AccountDao(database);
        TransactionDao transactionDao = new TransactionDao(database);
        CategoryDao categoryDao = new CategoryDao(database);
        BudgetDao budgetDao = new BudgetDao(database);
        RecurringRuleDao ruleDao = new RecurringRuleDao(database);
        AIInsightDao insightDao = new AIInsightDao(database);

        budgetService = new BudgetService(budgetDao, transactionDao, categoryDao, new BudgetEventBus());
        transactionService = new TransactionService(transactionDao, accountDao,
                new RuleBasedCategorizationStrategy(categoryDao, ruleDao), budgetService);
        ReportService reportService =
                new ReportService(transactionDao, budgetDao, categoryDao, budgetService);
        AnomalyService anomalyService = new AnomalyService(
                transactionDao, categoryDao, insightDao, new NullAIProvider());

        advisor = new DashboardAdvisor(new NullAIProvider(), reportService, anomalyService);

        account = accountDao.insert(
                new Account(null, "Advisor Test Account", AccountType.CHECKING, 500_000, "BDT"));
        diningCategory = categoryDao.insert(
                new Category(null, "Test Dining", CategoryType.EXPENSE)).getId();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void spend(double amount) {
        transactionService.create(new Transaction(null, account.getId(), diningCategory,
                -amount, MONTH.atDay(1), "Test spend", false));
    }

    @Test
    @DisplayName("names the breached budget and gives a next step")
    void namesTheBreachAndANextStep() {
        budgetService.setTarget(diningCategory, MONTH, 1_000);
        spend(1_600);

        String briefing = advisor.gather(MONTH).fallback();

        assertTrue(briefing.contains("Test Dining"), briefing);
        assertTrue(briefing.contains("600.00"), briefing);
        assertTrue(briefing.contains("Next step:"), briefing);
    }

    @Test
    @DisplayName("says there is nothing to act on when every budget holds")
    void quietMonthReadsAsQuiet() {
        budgetService.setTarget(diningCategory, MONTH, 1_000);
        spend(100);

        String briefing = advisor.gather(MONTH).fallback();

        assertTrue(briefing.contains("within its limit"), briefing);
        assertFalse(briefing.contains("Next step:"), briefing);
    }

    @Test
    @DisplayName("points at the Budgets tab when nothing is budgeted")
    void unbudgetedMonthSuggestsSettingOne() {
        spend(400);

        String briefing = advisor.gather(MONTH).fallback();

        assertTrue(briefing.contains("No budgets are set"), briefing);
        assertTrue(briefing.contains("Next step:"), briefing);
    }

    @Test
    @DisplayName("falls back to the figures when no model answers")
    void withoutAModelTheFiguresSpeak() {
        budgetService.setTarget(diningCategory, MONTH, 1_000);
        spend(1_600);

        DashboardAdvisor.Briefing briefing = advisor.gather(MONTH);
        DashboardAdvisor.Overview overview = advisor.explain(briefing);

        assertFalse(overview.fromModel());
        assertTrue(overview.text().equals(briefing.fallback()), overview.text());
    }
}
