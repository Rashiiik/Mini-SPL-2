package com.smartbudget.model;

import java.util.Objects;

/**
 * A place money sits. For {@link AccountType#CREDIT}, {@code balance} is the
 * outstanding amount owed rather than money available.
 */
public class Account {

    private Integer id;
    private String name;
    private AccountType type;
    private double balance;
    private String currency;

    public Account() {
        this.currency = "BDT";
    }

    public Account(Integer id, String name, AccountType type, double balance, String currency) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.balance = balance;
        this.currency = currency;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return id != null && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
