package com.smartbudget.persistence;

import com.smartbudget.model.AIInsight;
import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Budget;
import com.smartbudget.model.Category;
import com.smartbudget.model.CategoryType;
import com.smartbudget.model.Frequency;
import com.smartbudget.model.InsightKind;
import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.RuleStatus;
import com.smartbudget.model.Transaction;
import com.smartbudget.model.UserAction;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip coverage for every DAO: insert, read back, update, delete. */
class DaoCrudTest {

    private Database database;
    private AccountDao accountDao;
    private CategoryDao categoryDao;
    private TransactionDao transactionDao;
    private BudgetDao budgetDao;
    private RecurringRuleDao ruleDao;
    private AIInsightDao insightDao;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        accountDao = new AccountDao(database);
        categoryDao = new CategoryDao(database);
        transactionDao = new TransactionDao(database);
        budgetDao = new BudgetDao(database);
        ruleDao = new RecurringRuleDao(database);
        insightDao = new AIInsightDao(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("account round-trips through insert, update, and delete")
    void accountCrud() {
        Account account = accountDao.insert(
                new Account(null, "Test Wallet", AccountType.SAVINGS, 1000.0, "BDT"));
        assertNotNull(account.getId());

        account.setName("Renamed Wallet");
        account.setBalance(2500.0);
        accountDao.update(account);

        Account reloaded = accountDao.findById(account.getId()).orElseThrow();
        assertEquals("Renamed Wallet", reloaded.getName());
        assertEquals(2500.0, reloaded.getBalance(), 0.001);
        assertEquals(AccountType.SAVINGS, reloaded.getType());

        accountDao.delete(account.getId());
        assertTrue(accountDao.findById(account.getId()).isEmpty());
    }

    @Test
    @DisplayName("adjustBalance applies a signed delta to the stored balance")
    void adjustBalance() {
        Account account = accountDao.insert(
                new Account(null, "Delta Account", AccountType.CHECKING, 1000.0, "BDT"));

        accountDao.adjustBalance(account.getId(), -250.0);
        assertEquals(750.0, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);

        accountDao.adjustBalance(account.getId(), 100.0);
        assertEquals(850.0, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("category round-trips and is findable by name, case-insensitively")
    void categoryCrud() {
        Category category = categoryDao.insert(new Category(null, "Books", CategoryType.EXPENSE));
        assertNotNull(category.getId());

        assertTrue(categoryDao.findByName("books").isPresent());

        category.setName("Books & Media");
        categoryDao.update(category);
        assertEquals("Books & Media", categoryDao.findById(category.getId()).orElseThrow().getName());

        categoryDao.delete(category.getId());
        assertTrue(categoryDao.findById(category.getId()).isEmpty());
    }

    @Test
    @DisplayName("transaction round-trips and keeps its signed amount and date")
    void transactionCrud() {
        int accountId = accountDao.findAll().get(0).getId();
        int categoryId = categoryDao.findByName("Groceries").orElseThrow().getId();
        LocalDate date = LocalDate.now().withDayOfMonth(2);

        Transaction transaction = transactionDao.insert(
                new Transaction(null, accountId, categoryId, -1234.50, date, "Test purchase", false));
        assertNotNull(transaction.getId());

        Transaction reloaded = transactionDao.findById(transaction.getId()).orElseThrow();
        assertEquals(-1234.50, reloaded.getAmount(), 0.001);
        assertEquals(date, reloaded.getDate());
        assertTrue(reloaded.isExpense());
        assertEquals(1234.50, reloaded.getAbsoluteAmount(), 0.001);

        reloaded.setDescription("Edited purchase");
        reloaded.setAmount(-1500.0);
        transactionDao.update(reloaded);

        Transaction updated = transactionDao.findById(transaction.getId()).orElseThrow();
        assertEquals("Edited purchase", updated.getDescription());
        assertEquals(-1500.0, updated.getAmount(), 0.001);

        transactionDao.delete(transaction.getId());
        assertTrue(transactionDao.findById(transaction.getId()).isEmpty());
    }

    @Test
    @DisplayName("an uncategorised transaction is stored and read back with a null category")
    void transactionAllowsNullCategory() {
        int accountId = accountDao.findAll().get(0).getId();
        Transaction transaction = transactionDao.insert(new Transaction(
                null, accountId, null, -99.0, LocalDate.now(), "Unknown merchant", false));

        assertNull(transactionDao.findById(transaction.getId()).orElseThrow().getCategoryId());
    }

    @Test
    @DisplayName("saving a budget twice for one category and month updates rather than duplicates")
    void budgetSaveIsUpsert() {
        int categoryId = categoryDao.findByName("Groceries").orElseThrow().getId();
        YearMonth month = YearMonth.of(2030, 1);

        budgetDao.save(new Budget(null, categoryId, month, 5000.0));
        budgetDao.save(new Budget(null, categoryId, month, 7500.0));

        assertEquals(1, budgetDao.findByMonth(month).size());
        assertEquals(7500.0,
                budgetDao.findByCategoryAndMonth(categoryId, month).orElseThrow().getTargetAmount(),
                0.001);
    }

    @Test
    @DisplayName("recurring rule round-trips and is findable by status")
    void recurringRuleCrud() {
        int categoryId = categoryDao.findByName("Subscriptions").orElseThrow().getId();

        RecurringRule rule = ruleDao.insert(new RecurringRule(
                null, "hoichoi", categoryId, -299.0, Frequency.MONTHLY, RuleStatus.ACTIVE));
        assertNotNull(rule.getId());

        rule.setStatus(RuleStatus.FLAGGED);
        ruleDao.update(rule);

        assertTrue(ruleDao.findByStatus(RuleStatus.FLAGGED).stream()
                .anyMatch(r -> "hoichoi".equals(r.getDescriptionPattern())));

        ruleDao.delete(rule.getId());
        assertTrue(ruleDao.findById(rule.getId()).isEmpty());
    }

    @Test
    @DisplayName("rule matching is case-insensitive and substring-based")
    void ruleMatching() {
        RecurringRule rule = new RecurringRule(
                null, "netflix", null, -599.0, Frequency.MONTHLY, RuleStatus.ACTIVE);

        assertTrue(rule.matches("NETFLIX subscription"));
        assertTrue(rule.matches("Payment to Netflix Inc"));
        assertTrue(!rule.matches("Spotify Premium"));
        assertTrue(!rule.matches(null));
    }

    @Test
    @DisplayName("an insight round-trips and records the user's decision")
    void insightCrud() {
        int transactionId = transactionDao.findAll().get(0).getId();

        AIInsight insight = insightDao.insert(AIInsight.forTransaction(
                transactionId, InsightKind.ANOMALY, "This is far above your usual spend here."));
        assertNotNull(insight.getId());
        assertEquals(UserAction.PENDING, insight.getUserAction());

        insightDao.updateUserAction(insight.getId(), UserAction.ACCEPTED);

        AIInsight reloaded = insightDao.findByTransaction(transactionId).orElseThrow();
        assertEquals(UserAction.ACCEPTED, reloaded.getUserAction());
        assertNotNull(reloaded.getCreatedAt());
        assertNull(reloaded.getRelatedReportMonth());
    }

    @Test
    @DisplayName("a report insight round-trips against a month rather than a transaction")
    void reportInsightRoundTrips() {
        YearMonth month = YearMonth.of(2030, 3);
        insightDao.insert(AIInsight.forReport(month, "Spending was steady this month."));

        Optional<AIInsight> found = insightDao.findByMonth(month).stream().findFirst();
        assertTrue(found.isPresent());
        assertEquals(InsightKind.REPORT, found.get().getKind());
        assertNull(found.get().getRelatedTransactionId());
    }

    @Test
    @DisplayName("spending breakdown aggregates expenses by category for a month")
    void spendingBreakdown() {
        Map<String, Double> breakdown = transactionDao.spendingByCategory(YearMonth.now());

        assertTrue(breakdown.containsKey("Rent"), "seeded rent should appear");
        assertTrue(breakdown.values().stream().allMatch(total -> total > 0),
                "breakdown totals are reported as positive amounts");
        assertEquals(22000.0, breakdown.get("Rent"), 0.001);
    }

    @Test
    @DisplayName("category total for a month excludes income and other months")
    void categoryMonthTotal() {
        int groceries = categoryDao.findByName("Groceries").orElseThrow().getId();

        double thisMonth = transactionDao.sumExpensesByCategoryAndMonth(groceries, YearMonth.now());
        assertEquals(3400.0, thisMonth, 0.001);

        double farFuture = transactionDao.sumExpensesByCategoryAndMonth(groceries, YearMonth.of(2030, 6));
        assertEquals(0.0, farFuture, 0.001);
    }
}
