package com.smartbudget.pattern.factory;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.service.ValidationException;

/**
 * Builds accounts with the initial state their type requires.
 *
 * <p><b>Problem it solves:</b> the three account types do not start out alike.
 * A credit card begins at zero debt and its balance means the opposite of a
 * chequing balance, while savings and chequing begin from an opening deposit.
 * Left to the caller, every screen that creates an account would have to repeat
 * those rules and would eventually get one of them wrong.
 *
 * <p><b>Why Factory Method:</b> it puts the type-specific construction rules in
 * one place. Adding a wallet or investment account later means adding a branch
 * here, not hunting for construction sites across the UI.
 *
 * <p><b>Alternative considered:</b> a plain constructor with defaults. That
 * cannot express "a credit account may not be opened with a positive available
 * balance" without putting a conditional inside the model, which would mix
 * validation into what should be a passive data holder.
 */
public class AccountFactory {

    private static final String DEFAULT_CURRENCY = "BDT";

    public Account create(AccountType type, String name, double openingBalance, String currency) {
        if (type == null) {
            throw new ValidationException("Please choose an account type.");
        }
        if (name == null || name.isBlank()) {
            throw new ValidationException("Account name cannot be empty.");
        }

        String resolvedCurrency = (currency == null || currency.isBlank())
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase();
        if (resolvedCurrency.length() != 3) {
            throw new ValidationException("Currency must be a three-letter code, for example BDT.");
        }

        double balance = switch (type) {
            // For a credit card, balance is debt owed. A new card carries the
            // amount already outstanding, which can never be negative.
            case CREDIT -> {
                if (openingBalance < 0) {
                    throw new ValidationException(
                            "A credit card's outstanding balance cannot be negative.");
                }
                yield openingBalance;
            }
            // Deposit accounts hold money available, which cannot start overdrawn.
            case CHECKING, SAVINGS -> {
                if (openingBalance < 0) {
                    throw new ValidationException("Opening balance cannot be negative.");
                }
                yield openingBalance;
            }
        };

        return new Account(null, name.trim(), type, balance, resolvedCurrency);
    }

    /** Convenience for the common case: a new, empty account in the default currency. */
    public Account create(AccountType type, String name) {
        return create(type, name, 0.0, DEFAULT_CURRENCY);
    }
}
