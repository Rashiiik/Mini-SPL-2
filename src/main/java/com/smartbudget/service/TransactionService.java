package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.pattern.strategy.CategorizationStrategy;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * The Categorise &amp; Alert workflow: validate, categorise, save, keep the
 * account balance correct, and let the budget rules decide whether to warn.
 *
 * <p>Depends on {@link CategorizationStrategy} as an interface, never on a
 * concrete implementation, which is what allows the AI strategy to be swapped in
 * later without touching this class.
 */
public class TransactionService {

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final CategorizationStrategy categorizationStrategy;
    private final BudgetService budgetService;

    public TransactionService(TransactionDao transactionDao, AccountDao accountDao,
                              CategorizationStrategy categorizationStrategy,
                              BudgetService budgetService) {
        this.transactionDao = transactionDao;
        this.accountDao = accountDao;
        this.categorizationStrategy = categorizationStrategy;
        this.budgetService = budgetService;
    }

    /**
     * How a transaction moves an account's stored balance.
     *
     * <p>For chequing and savings the balance is money available, so a negative
     * amount reduces it. For a credit card the balance is debt owed, so a
     * purchase increases it — the sign is inverted. Getting this backwards is
     * the easiest way to corrupt every figure in the application, so it lives in
     * exactly one place.
     */
    private static double balanceDelta(AccountType type, double amount) {
        return type == AccountType.CREDIT ? -amount : amount;
    }

    public Transaction create(Transaction transaction) {
        validate(transaction);
        Account account = requireAccount(transaction.getAccountId());

        if (transaction.getCategoryId() == null) {
            categorizationStrategy.categorize(transaction)
                    .map(Category::getId)
                    .ifPresent(transaction::setCategoryId);
        }

        Transaction saved = transactionDao.insert(transaction);
        accountDao.adjustBalance(account.getId(), balanceDelta(account.getType(), saved.getAmount()));
        budgetService.evaluateAfter(saved);
        return saved;
    }

    /**
     * Saves an edited transaction, reversing the old balance effect before
     * applying the new one so a change of amount, or of account, leaves both
     * accounts correct.
     */
    public Transaction update(Transaction transaction) {
        if (transaction.getId() == null) {
            throw new ValidationException("Cannot update a transaction that was never saved.");
        }
        validate(transaction);

        Transaction existing = transactionDao.findById(transaction.getId())
                .orElseThrow(() -> new ValidationException("That transaction no longer exists."));
        Account oldAccount = requireAccount(existing.getAccountId());
        Account newAccount = requireAccount(transaction.getAccountId());

        accountDao.adjustBalance(oldAccount.getId(),
                -balanceDelta(oldAccount.getType(), existing.getAmount()));
        transactionDao.update(transaction);
        accountDao.adjustBalance(newAccount.getId(),
                balanceDelta(newAccount.getType(), transaction.getAmount()));

        budgetService.evaluateAfter(transaction);
        return transaction;
    }

    public void delete(int transactionId) {
        Transaction existing = transactionDao.findById(transactionId)
                .orElseThrow(() -> new ValidationException("That transaction no longer exists."));
        Account account = requireAccount(existing.getAccountId());

        accountDao.adjustBalance(account.getId(),
                -balanceDelta(account.getType(), existing.getAmount()));
        transactionDao.delete(transactionId);
    }

    /**
     * The category this strategy would suggest, without saving anything.
     * Lets the UI pre-fill the dropdown and still let the user override it.
     */
    public Optional<Category> suggestCategory(Transaction transaction) {
        return categorizationStrategy.categorize(transaction);
    }

    public String strategyName() {
        return categorizationStrategy.name();
    }

    public List<Transaction> findAll() {
        return transactionDao.findAll();
    }

    public List<Transaction> findByAccount(int accountId) {
        return transactionDao.findByAccount(accountId);
    }

    public List<Transaction> findByMonth(YearMonth month) {
        return transactionDao.findByMonth(month);
    }

    public List<Transaction> findBetween(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("The start date must be on or before the end date.");
        }
        return transactionDao.findBetween(from, to);
    }

    public Optional<Transaction> findById(int id) {
        return transactionDao.findById(id);
    }

    /** Re-evaluates a category's budget standing, used to refresh the dashboard. */
    public Optional<BudgetAlert> currentStatus(Transaction transaction) {
        return budgetService.evaluateAfter(transaction);
    }

    private void validate(Transaction transaction) {
        if (transaction == null) {
            throw new ValidationException("No transaction was provided.");
        }
        if (transaction.getAmount() == 0) {
            throw new ValidationException("Amount cannot be zero.");
        }
        if (transaction.getDate() == null) {
            throw new ValidationException("Please choose a date.");
        }
        if (transaction.getDate().isAfter(LocalDate.now())) {
            throw new ValidationException("A transaction cannot be dated in the future.");
        }
        if (transaction.getDescription() == null || transaction.getDescription().isBlank()) {
            throw new ValidationException("Please enter a description.");
        }
        transaction.setDescription(transaction.getDescription().trim());
    }

    private Account requireAccount(int accountId) {
        return accountDao.findById(accountId)
                .orElseThrow(() -> new ValidationException("That account no longer exists."));
    }
}
