package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.pattern.factory.AccountFactory;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.util.List;
import java.util.Optional;

/** CRUD for accounts, with creation routed through {@link AccountFactory}. */
public class AccountService {

    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final AccountFactory accountFactory;

    public AccountService(AccountDao accountDao, TransactionDao transactionDao,
                          AccountFactory accountFactory) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.accountFactory = accountFactory;
    }

    public Account create(AccountType type, String name, double openingBalance, String currency) {
        Account account = accountFactory.create(type, name, openingBalance, currency);
        requireUniqueName(account.getName(), null);
        return accountDao.insert(account);
    }

    public void rename(int accountId, String newName) {
        Account account = require(accountId);
        if (newName == null || newName.isBlank()) {
            throw new ValidationException("Account name cannot be empty.");
        }
        requireUniqueName(newName.trim(), accountId);
        account.setName(newName.trim());
        accountDao.update(account);
    }

    /**
     * Deletes an account and, by the schema's cascade rule, its transactions.
     * The caller is told how many will go so the UI can confirm first.
     */
    public int transactionCount(int accountId) {
        return transactionDao.findByAccount(accountId).size();
    }

    public void delete(int accountId) {
        require(accountId);
        accountDao.delete(accountId);
    }

    public List<Account> findAll() {
        return accountDao.findAll();
    }

    public Optional<Account> findById(int accountId) {
        return accountDao.findById(accountId);
    }

    /**
     * Net worth: deposits minus what is owed on credit accounts, since a credit
     * balance is debt rather than an asset.
     */
    public double netWorth() {
        return accountDao.findAll().stream()
                .mapToDouble(a -> a.getType() == AccountType.CREDIT ? -a.getBalance() : a.getBalance())
                .sum();
    }

    private Account require(int accountId) {
        return accountDao.findById(accountId)
                .orElseThrow(() -> new ValidationException("That account no longer exists."));
    }

    private void requireUniqueName(String name, Integer excludingId) {
        boolean taken = accountDao.findAll().stream()
                .filter(a -> excludingId == null || !a.getId().equals(excludingId))
                .anyMatch(a -> a.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ValidationException("An account named \"" + name + "\" already exists.");
        }
    }
}
