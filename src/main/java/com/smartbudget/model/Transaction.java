package com.smartbudget.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single movement of money.
 *
 * <p>{@code amount} is signed: negative is money out, positive is money in. The
 * sign is the single source of truth for direction — the category is only a
 * label, and an uncategorised transaction ({@code categoryId == null}) is still
 * a valid, fully usable record.
 */
public class Transaction {

    private Integer id;
    private int accountId;
    private Integer categoryId;
    private double amount;
    private LocalDate date;
    private String description;
    private boolean recurring;

    public Transaction() {
    }

    public Transaction(Integer id, int accountId, Integer categoryId, double amount,
                       LocalDate date, String description, boolean recurring) {
        this.id = id;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.recurring = recurring;
    }

    /** True when this transaction takes money out of the account. */
    public boolean isExpense() {
        return amount < 0;
    }

    /** Magnitude without the sign, for display and for budget arithmetic. */
    public double getAbsoluteAmount() {
        return Math.abs(amount);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction transaction)) {
            return false;
        }
        return id != null && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return date + " " + description + " " + amount;
    }
}
