package com.smartbudget.model;

import java.time.YearMonth;
import java.util.Objects;

/**
 * A spending target for one category in one month.
 *
 * <p>{@code targetAmount} is always positive — it is a limit, not a signed
 * movement of money, so it is compared against the absolute value of spend.
 */
public class Budget {

    private Integer id;
    private int categoryId;
    private YearMonth month;
    private double targetAmount;

    public Budget() {
    }

    public Budget(Integer id, int categoryId, YearMonth month, double targetAmount) {
        this.id = id;
        this.categoryId = categoryId;
        this.month = month;
        this.targetAmount = targetAmount;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public YearMonth getMonth() {
        return month;
    }

    public void setMonth(YearMonth month) {
        this.month = month;
    }

    public double getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(double targetAmount) {
        this.targetAmount = targetAmount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Budget budget)) {
            return false;
        }
        return id != null && id.equals(budget.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Budget[category=" + categoryId + ", month=" + month + ", target=" + targetAmount + "]";
    }
}
