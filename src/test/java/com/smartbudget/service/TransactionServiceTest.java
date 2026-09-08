package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.factory.AccountFactory;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the balance arithmetic and validation rules — the business logic most
 * likely to be probed in the demo, and the easiest to get silently wrong.
 */
class TransactionServiceTest {

    private Database database;
    private AccountDao accountDao;
    private TransactionService transactionService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        accountDao = new AccountDao(database);
        TransactionDao transactionDao = new TransactionDao(database);
        CategoryDao categoryDao = new CategoryDao(database);
        BudgetDao budgetDao = new BudgetDao(database);
        RecurringRuleDao ruleDao = new RecurringRuleDao(database);

        BudgetEventBus eventBus = new BudgetEventBus();
        BudgetService budgetService =
                new BudgetService(budgetDao, transactionDao, categoryDao, eventBus);
        transactionService = new TransactionService(transactionDao, accountDao,
                new RuleBasedCategorizationStrategy(categoryDao, ruleDao), budgetService);
        accountService = new AccountService(accountDao, transactionDao, new AccountFactory());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private Account freshAccount(AccountType type, double opening) {
        return accountService.create(type, "Test " + type + " " + System.nanoTime(), opening, "BDT");
    }

    private Transaction expense(int accountId, double amount) {
        return new Transaction(null, accountId, null, amount, LocalDate.now(), "Test spend", false);
    }

    @Test
    @DisplayName("spending from a chequing account reduces its balance")
    void chequingExpenseReducesBalance() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);

        transactionService.create(expense(account.getId(), -1_500));

        assertEquals(8_500, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("income into a savings account increases its balance")
    void savingsIncomeIncreasesBalance() {
        Account account = freshAccount(AccountType.SAVINGS, 5_000);

        transactionService.create(expense(account.getId(), 2_000));

        assertEquals(7_000, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("spending on a credit card increases the balance, because it is debt owed")
    void creditExpenseIncreasesDebt() {
        Account account = freshAccount(AccountType.CREDIT, 2_000);

        transactionService.create(expense(account.getId(), -1_500));

        assertEquals(3_500, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("paying off a credit card reduces the balance owed")
    void creditPaymentReducesDebt() {
        Account account = freshAccount(AccountType.CREDIT, 5_000);

        transactionService.create(expense(account.getId(), 2_000));

        assertEquals(3_000, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("the same expense moves a credit balance opposite to a chequing balance")
    void creditAndChequingMoveInOppositeDirections() {
        Account chequing = freshAccount(AccountType.CHECKING, 10_000);
        Account credit = freshAccount(AccountType.CREDIT, 10_000);

        transactionService.create(expense(chequing.getId(), -1_000));
        transactionService.create(expense(credit.getId(), -1_000));

        assertEquals(9_000, accountDao.findById(chequing.getId()).orElseThrow().getBalance(), 0.001);
        assertEquals(11_000, accountDao.findById(credit.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("editing the amount leaves the balance consistent with the new value only")
    void updateReversesOldAmountBeforeApplyingNew() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        Transaction saved = transactionService.create(expense(account.getId(), -1_000));

        saved.setAmount(-2_500);
        transactionService.update(saved);

        assertEquals(7_500, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("moving a transaction to another account corrects both balances")
    void updateAcrossAccountsCorrectsBoth() {
        Account from = freshAccount(AccountType.CHECKING, 10_000);
        Account to = freshAccount(AccountType.SAVINGS, 10_000);
        Transaction saved = transactionService.create(expense(from.getId(), -1_000));

        saved.setAccountId(to.getId());
        transactionService.update(saved);

        assertEquals(10_000, accountDao.findById(from.getId()).orElseThrow().getBalance(), 0.001);
        assertEquals(9_000, accountDao.findById(to.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("deleting a transaction restores the balance exactly")
    void deleteRestoresBalance() {
        Account account = freshAccount(AccountType.CREDIT, 1_000);
        Transaction saved = transactionService.create(expense(account.getId(), -750));

        transactionService.delete(saved.getId());

        assertEquals(1_000, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }

    @Test
    @DisplayName("an uncategorised transaction is categorised on save by the strategy")
    void categorisesOnSave() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        Transaction transaction = new Transaction(
                null, account.getId(), null, -450, LocalDate.now(), "Uber to office", false);

        Transaction saved = transactionService.create(transaction);

        assertNotNull(saved.getCategoryId(), "strategy should have supplied a category");
    }

    @Test
    @DisplayName("an unrecognised merchant is saved uncategorised rather than guessed at")
    void unknownMerchantStaysUncategorised() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        Transaction transaction = new Transaction(
                null, account.getId(), null, -450, LocalDate.now(), "Zzyzx Holdings Ltd", false);

        assertNull(transactionService.create(transaction).getCategoryId());
    }

    @Test
    @DisplayName("a category chosen by the user is never overwritten by the strategy")
    void userCategoryWins() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        int rentCategory = new CategoryDao(database).findByName("Rent").orElseThrow().getId();
        Transaction transaction = new Transaction(null, account.getId(), rentCategory,
                -450, LocalDate.now(), "Uber to office", false);

        assertEquals(rentCategory, transactionService.create(transaction).getCategoryId());
    }

    @Test
    @DisplayName("a zero amount is rejected")
    void zeroAmountRejected() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        ValidationException error = assertThrows(ValidationException.class,
                () -> transactionService.create(expense(account.getId(), 0)));
        assertEquals("Amount cannot be zero.", error.getMessage());
    }

    @Test
    @DisplayName("a future-dated transaction is rejected")
    void futureDateRejected() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        Transaction transaction = expense(account.getId(), -100);
        transaction.setDate(LocalDate.now().plusDays(1));

        assertThrows(ValidationException.class, () -> transactionService.create(transaction));
    }

    @Test
    @DisplayName("a blank description is rejected")
    void blankDescriptionRejected() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);
        Transaction transaction = expense(account.getId(), -100);
        transaction.setDescription("   ");

        assertThrows(ValidationException.class, () -> transactionService.create(transaction));
    }

    @Test
    @DisplayName("a transaction against a missing account is rejected")
    void unknownAccountRejected() {
        assertThrows(ValidationException.class, () -> transactionService.create(expense(9_999, -100)));
    }

    @Test
    @DisplayName("a failed validation leaves the account balance untouched")
    void failedValidationDoesNotChangeBalance() {
        Account account = freshAccount(AccountType.CHECKING, 10_000);

        assertThrows(ValidationException.class,
                () -> transactionService.create(expense(account.getId(), 0)));

        assertEquals(10_000, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
    }
}
