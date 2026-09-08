package com.smartbudget.service;

import com.smartbudget.model.Category;
import com.smartbudget.model.Frequency;
import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.RuleStatus;
import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds repeating charges in the transaction history, and flags the ones worth
 * a second look.
 *
 * <p>The Subscription Review workflow: detect a recurring charge, decide whether
 * it looks like creep, and let the user confirm, cancel or ignore it. Detection
 * is entirely local arithmetic over dates and amounts — no AI is involved in
 * deciding, only in phrasing.
 */
public class SubscriptionService {

    /** How many occurrences before a repeated charge counts as recurring. */
    private static final int MINIMUM_OCCURRENCES = 3;

    /** Amounts within this fraction of each other count as "the same charge". */
    private static final double AMOUNT_TOLERANCE = 0.15;

    /** Gap in days that reads as monthly, allowing for billing-date drift. */
    private static final int MONTHLY_MIN_GAP = 24;
    private static final int MONTHLY_MAX_GAP = 38;

    /** A monthly charge unseen for this long looks abandoned or already cancelled. */
    private static final int STALE_DAYS = 50;

    /** Growth above this fraction between first and latest charge reads as a price rise. */
    private static final double PRICE_RISE_TOLERANCE = 0.20;

    private final TransactionDao transactionDao;
    private final RecurringRuleDao ruleDao;
    private final CategoryDao categoryDao;

    public SubscriptionService(TransactionDao transactionDao, RecurringRuleDao ruleDao,
                               CategoryDao categoryDao) {
        this.transactionDao = transactionDao;
        this.ruleDao = ruleDao;
        this.categoryDao = categoryDao;
    }

    /**
     * Groups the history by normalised description and returns those that repeat
     * on a roughly monthly cadence at a roughly constant amount.
     */
    public List<DetectedSubscription> detect() {
        Map<String, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction transaction : transactionDao.findAll()) {
            if (!transaction.isExpense() || transaction.getDescription() == null) {
                continue;
            }
            grouped.computeIfAbsent(normalise(transaction.getDescription()), key -> new ArrayList<>())
                    .add(transaction);
        }

        List<DetectedSubscription> detected = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            toSubscription(entry.getKey(), entry.getValue()).ifPresent(detected::add);
        }
        return detected;
    }

    private Optional<DetectedSubscription> toSubscription(String pattern, List<Transaction> group) {
        if (group.size() < MINIMUM_OCCURRENCES) {
            return Optional.empty();
        }

        List<Transaction> ordered = group.stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .toList();

        if (!amountsAreConsistent(ordered) || !cadenceIsMonthly(ordered)) {
            return Optional.empty();
        }

        Transaction first = ordered.get(0);
        Transaction latest = ordered.get(ordered.size() - 1);
        double averageAmount = ordered.stream()
                .mapToDouble(Transaction::getAbsoluteAmount)
                .average()
                .orElse(0);

        return Optional.of(new DetectedSubscription(
                pattern, ordered.size(), averageAmount,
                first.getAbsoluteAmount(), latest.getAbsoluteAmount(),
                first.getDate(), latest.getDate(), latest.getCategoryId()));
    }

    /** Every charge must sit within the tolerance band around the average. */
    private boolean amountsAreConsistent(List<Transaction> ordered) {
        double average = ordered.stream()
                .mapToDouble(Transaction::getAbsoluteAmount)
                .average()
                .orElse(0);
        if (average <= 0) {
            return false;
        }
        // A price rise across a long history would otherwise disqualify a genuine
        // subscription, so the band is generous rather than strict.
        return ordered.stream().allMatch(t ->
                Math.abs(t.getAbsoluteAmount() - average) / average <= AMOUNT_TOLERANCE * 2);
    }

    /** The average gap between consecutive charges must read as monthly. */
    private boolean cadenceIsMonthly(List<Transaction> ordered) {
        long totalGap = 0;
        for (int i = 1; i < ordered.size(); i++) {
            totalGap += ChronoUnit.DAYS.between(ordered.get(i - 1).getDate(), ordered.get(i).getDate());
        }
        double averageGap = (double) totalGap / (ordered.size() - 1);
        return averageGap >= MONTHLY_MIN_GAP && averageGap <= MONTHLY_MAX_GAP;
    }

    /**
     * Which detected subscriptions look like creep, and why.
     *
     * <p>Three signals, each a distinct real problem: a charge that has quietly
     * stopped appearing but was never cancelled, a charge whose price has crept
     * up, and the accumulated monthly total that no single charge reveals.
     */
    public List<SubscriptionFinding> findCreep() {
        List<SubscriptionFinding> findings = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (DetectedSubscription subscription : detect()) {
            long daysSinceLast = ChronoUnit.DAYS.between(subscription.lastSeen(), today);

            if (daysSinceLast > STALE_DAYS) {
                findings.add(new SubscriptionFinding(subscription, CreepReason.ABANDONED,
                        String.format("No charge for %d days, but the subscription was never cancelled.",
                                daysSinceLast)));
                continue;
            }

            double rise = subscription.firstAmount() > 0
                    ? (subscription.latestAmount() - subscription.firstAmount()) / subscription.firstAmount()
                    : 0;
            if (rise > PRICE_RISE_TOLERANCE) {
                findings.add(new SubscriptionFinding(subscription, CreepReason.PRICE_INCREASE,
                        String.format("The charge rose from %.2f to %.2f, up %.0f%% since %s.",
                                subscription.firstAmount(), subscription.latestAmount(),
                                rise * 100, subscription.firstSeen())));
            }
        }
        return findings;
    }

    /** Combined monthly cost of everything detected as recurring. */
    public double monthlyTotal() {
        return detect().stream().mapToDouble(DetectedSubscription::averageAmount).sum();
    }

    /**
     * Persists detected subscriptions as recurring rules, so the categorisation
     * strategy can use them and the user can review them later.
     *
     * <p>Rules the user has already acted on are left alone: re-running detection
     * must never resurrect something they cancelled or chose to ignore.
     */
    public int saveDetectedRules() {
        List<RecurringRule> existing = ruleDao.findAll();
        int created = 0;

        for (DetectedSubscription subscription : detect()) {
            boolean known = existing.stream()
                    .anyMatch(rule -> rule.getDescriptionPattern()
                            .equalsIgnoreCase(subscription.pattern()));
            if (known) {
                continue;
            }
            ruleDao.insert(new RecurringRule(null, subscription.pattern(),
                    subscription.categoryId(), -subscription.averageAmount(),
                    Frequency.MONTHLY, RuleStatus.ACTIVE));
            created++;
        }
        return created;
    }

    /** Marks every rule matching a creep finding as flagged, for the review screen. */
    public int flagCreep() {
        int flagged = 0;
        for (SubscriptionFinding finding : findCreep()) {
            for (RecurringRule rule : ruleDao.findAll()) {
                if (rule.getDescriptionPattern().equalsIgnoreCase(finding.subscription().pattern())
                        && rule.getStatus() == RuleStatus.ACTIVE) {
                    rule.setStatus(RuleStatus.FLAGGED);
                    ruleDao.update(rule);
                    flagged++;
                }
            }
        }
        return flagged;
    }

    public List<RecurringRule> rulesFor(RuleStatus status) {
        return ruleDao.findByStatus(status);
    }

    public List<RecurringRule> allRules() {
        return ruleDao.findAll();
    }

    /** The user's decision in the review workflow: confirm, cancel or ignore. */
    public void updateStatus(int ruleId, RuleStatus status) {
        RecurringRule rule = ruleDao.findById(ruleId)
                .orElseThrow(() -> new ValidationException("That subscription rule no longer exists."));
        rule.setStatus(status);
        ruleDao.update(rule);
    }

    public String categoryNameFor(Integer categoryId) {
        if (categoryId == null) {
            return "Uncategorised";
        }
        return categoryDao.findById(categoryId).map(Category::getName).orElse("Uncategorised");
    }

    /**
     * Reduces a description to a comparison key.
     *
     * <p>Digits and punctuation are stripped because statement descriptions carry
     * varying reference numbers — "NETFLIX 4471" and "NETFLIX 8823" are the same
     * subscription and must land in the same group.
     */
    static String normalise(String description) {
        return description.toLowerCase()
                .replaceAll("[0-9]+", " ")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record DetectedSubscription(
            String pattern,
            int occurrences,
            double averageAmount,
            double firstAmount,
            double latestAmount,
            LocalDate firstSeen,
            LocalDate lastSeen,
            Integer categoryId) {
    }

    public enum CreepReason {
        ABANDONED,
        PRICE_INCREASE
    }

    public record SubscriptionFinding(
            DetectedSubscription subscription,
            CreepReason reason,
            String explanation) {
    }
}
