package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers budget arithmetic and, critically, that the Observer fires on the
 * threshold crossing rather than on every later save.
 */
class BudgetServiceTest {

    private Database database;
    private BudgetService budgetService;
    private TransactionService transactionService;
    private CategoryDao categoryDao;
    private List<BudgetAlert> received;
    private Account account;
    private int diningCategory;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        AccountDao accountDao = new AccountDao(database);
        TransactionDao transactionDao = new TransactionDao(database);
        categoryDao = new CategoryDao(database);
        BudgetDao budgetDao = new BudgetDao(database);
        RecurringRuleDao ruleDao = new RecurringRuleDao(database);

        BudgetEventBus eventBus = new BudgetEventBus();
        received = new ArrayList<>();
        eventBus.subscribe(received::add);

        budgetService = new BudgetService(budgetDao, transactionDao, categoryDao, eventBus);
        transactionService = new TransactionService(transactionDao, accountDao,
                new RuleBasedCategorizationStrategy(categoryDao, ruleDao), budgetService);

        // A dedicated account and a category with no seeded spend this month, so
        // the arithmetic in each test starts from a known zero.
        account = accountDao.insert(
                new Account(null, "Budget Test Account", AccountType.CHECKING, 500_000, "BDT"));
        diningCategory = categoryDao.insert(
                new com.smartbudget.model.Category(null, "Test Dining",
                        com.smartbudget.model.CategoryType.EXPENSE)).getId();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void spend(double amount) {
        transactionService.create(new Transaction(null, account.getId(), diningCategory,
                -amount, LocalDate.now(), "Test spend", false));
    }

    @Test
    @DisplayName("utilisation is zero when no budget has been set")
    void utilisationWithoutBudget() {
        assertEquals(0.0, budgetService.utilisation(diningCategory, YearMonth.now()), 0.001);
    }

    @Test
    @DisplayName("utilisation tracks spend against the target")
    void utilisationTracksSpend() {
        YearMonth month = YearMonth.now();
        budgetService.setTarget(diningCategory, month, 1_000);

        assertEquals(0.0, budgetService.utilisation(diningCategory, month), 0.001);

        spend(500);
        assertEquals(0.5, budgetService.utilisation(diningCategory, month), 0.001);

        spend(500);
        assertEquals(1.0, budgetService.utilisation(diningCategory, month), 0.001);

        spend(200);
        assertEquals(1.2, budgetService.utilisation(diningCategory, month), 0.001);
    }

    @Test
    @DisplayName("setting a target twice updates rather than duplicating it")
    void setTargetUpserts() {
        YearMonth month = YearMonth.now();
        budgetService.setTarget(diningCategory, month, 1_000);
        budgetService.setTarget(diningCategory, month, 2_000);

        assertEquals(2_000,
                budgetService.targetFor(diningCategory, month).orElseThrow().getTargetAmount(), 0.001);
    }

    @Test
    @DisplayName("a non-positive target is rejected")
    void nonPositiveTargetRejected() {
        assertThrows(ValidationException.class,
                () -> budgetService.setTarget(diningCategory, YearMonth.now(), 0));
        assertThrows(ValidationException.class,
                () -> budgetService.setTarget(diningCategory, YearMonth.now(), -100));
    }

    @Test
    @DisplayName("crossing 80 percent raises a warning")
    void warningAtEightyPercent() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        spend(850);

        assertEquals(1, received.size());
        assertEquals(AlertSeverity.WARNING, received.get(0).severity());
    }

    @Test
    @DisplayName("going over the target raises an exceeded alert")
    void exceededOverTarget() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        spend(1_200);

        assertEquals(1, received.size());
        assertEquals(AlertSeverity.EXCEEDED, received.get(0).severity());
        assertEquals(200, received.get(0).overspend(), 0.001);
    }

    @Test
    @DisplayName("the alert fires once on the crossing, not on every later purchase")
    void alertFiresOnceOnCrossing() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        spend(1_200);
        assertEquals(1, received.size(), "the crossing should alert");

        spend(100);
        spend(100);
        assertEquals(1, received.size(), "already-over purchases must not re-alert");
    }

    @Test
    @DisplayName("severity escalating from warning to exceeded alerts a second time")
    void escalationAlertsAgain() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        spend(850);
        spend(300);

        assertEquals(2, received.size());
        assertEquals(AlertSeverity.WARNING, received.get(0).severity());
        assertEquals(AlertSeverity.EXCEEDED, received.get(1).severity());
    }

    @Test
    @DisplayName("spending well within budget raises nothing")
    void noAlertWhenComfortablyUnder() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        spend(100);
        spend(100);

        assertTrue(received.isEmpty());
    }

    @Test
    @DisplayName("a category with no budget never alerts")
    void noBudgetNoAlert() {
        spend(50_000);

        assertTrue(received.isEmpty());
    }

    @Test
    @DisplayName("income does not consume a budget")
    void incomeDoesNotConsumeBudget() {
        budgetService.setTarget(diningCategory, YearMonth.now(), 1_000);

        transactionService.create(new Transaction(null, account.getId(), diningCategory,
                5_000, LocalDate.now(), "Refund", false));

        assertTrue(received.isEmpty());
        assertEquals(0.0, budgetService.utilisation(diningCategory, YearMonth.now()), 0.001);
    }

    @Test
    @DisplayName("status listing reports every budgeted category with its severity")
    void statusListing() {
        YearMonth month = YearMonth.now();
        budgetService.setTarget(diningCategory, month, 1_000);
        spend(1_200);

        BudgetAlert status = budgetService.statusFor(month).stream()
                .filter(a -> a.categoryName().equals("Test Dining"))
                .findFirst()
                .orElseThrow();

        assertEquals(AlertSeverity.EXCEEDED, status.severity());
        assertEquals(1_200, status.spent(), 0.001);
    }

    @Test
    @DisplayName("a budget for a missing category is rejected")
    void unknownCategoryRejected() {
        assertThrows(ValidationException.class,
                () -> budgetService.setTarget(9_999, YearMonth.now(), 1_000));
    }
}
