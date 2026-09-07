package com.smartbudget.model;

import java.util.Objects;

/**
 * A repeating charge the system has learned about.
 *
 * <p>Serves two roles: it is the lookup table the rule-based categorisation
 * strategy matches against, and it is the unit the subscription-review workflow
 * acts on.
 */
public class RecurringRule {

    private Integer id;
    private String descriptionPattern;
    private Integer categoryId;
    private double expectedAmount;
    private Frequency frequency;
    private RuleStatus status;

    public RecurringRule() {
        this.status = RuleStatus.ACTIVE;
    }

    public RecurringRule(Integer id, String descriptionPattern, Integer categoryId,
                         double expectedAmount, Frequency frequency, RuleStatus status) {
        this.id = id;
        this.descriptionPattern = descriptionPattern;
        this.categoryId = categoryId;
        this.expectedAmount = expectedAmount;
        this.frequency = frequency;
        this.status = status;
    }

    /**
     * Whether a transaction description matches this rule.
     *
     * <p>Deliberately a plain case-insensitive substring test rather than a
     * regex: patterns are user-editable, and a malformed regex typed into the UI
     * should not be able to throw from inside categorisation.
     */
    public boolean matches(String description) {
        if (description == null || descriptionPattern == null) {
            return false;
        }
        return description.toLowerCase().contains(descriptionPattern.toLowerCase());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDescriptionPattern() {
        return descriptionPattern;
    }

    public void setDescriptionPattern(String descriptionPattern) {
        this.descriptionPattern = descriptionPattern;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public double getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(double expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public RuleStatus getStatus() {
        return status;
    }

    public void setStatus(RuleStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecurringRule rule)) {
            return false;
        }
        return id != null && id.equals(rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return descriptionPattern + " (" + frequency + ")";
    }
}
