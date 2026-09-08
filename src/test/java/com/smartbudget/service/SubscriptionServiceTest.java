package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.RuleStatus;
import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionServiceTest {

    private Database database;
    private TransactionDao transactionDao;
    private RecurringRuleDao ruleDao;
    private SubscriptionService subscriptionService;
    private int accountId;
    private Integer subscriptionsCategory;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        transactionDao = new TransactionDao(database);
        ruleDao = new RecurringRuleDao(database);
        CategoryDao categoryDao = new CategoryDao(database);

        subscriptionService = new SubscriptionService(transactionDao, ruleDao, categoryDao);

        accountId = new AccountDao(database).insert(
                new Account(null, "Subscription Test", AccountType.CREDIT, 0, "BDT")).getId();
        subscriptionsCategory = categoryDao.findByName("Subscriptions").orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    /** Charges a description monthly, counting back from a number of months ago. */
    private void chargeMonthly(String description, double amount, int monthsBack, int occurrences) {
        for (int i = 0; i < occurrences; i++) {
            LocalDate date = LocalDate.now().minusMonths(monthsBack - (long) i).withDayOfMonth(10);
            transactionDao.insert(new Transaction(null, accountId, subscriptionsCategory,
                    -amount, date, description, true));
        }
    }

    private boolean detected(String pattern) {
        return subscriptionService.detect().stream()
                .anyMatch(s -> s.pattern().contains(pattern));
    }

    @Test
    @DisplayName("a monthly charge at a steady amount is detected")
    void detectsMonthlyCharge() {
        chargeMonthly("Hoichoi subscription", 299, 4, 5);

        assertTrue(detected("hoichoi"));
    }

    @Test
    @DisplayName("two occurrences are not enough to call something recurring")
    void ignoresTooFewOccurrences() {
        chargeMonthly("Rare service", 500, 2, 2);

        assertFalse(detected("rare service"));
    }

    @Test
    @DisplayName("irregular one-off spending at a similar amount is not a subscription")
    void ignoresIrregularSpending() {
        // Three charges days apart rather than months: same amount, wrong cadence.
        for (int day = 1; day <= 3; day++) {
            transactionDao.insert(new Transaction(null, accountId, subscriptionsCategory,
                    -500, LocalDate.now().withDayOfMonth(day), "Coffee beans", false));
        }

        assertFalse(detected("coffee beans"));
    }

    @Test
    @DisplayName("wildly varying amounts are not treated as one subscription")
    void ignoresVaryingAmounts() {
        LocalDate base = LocalDate.now().minusMonths(3).withDayOfMonth(10);
        double[] amounts = {200, 4_000, 900};
        for (int i = 0; i < amounts.length; i++) {
            transactionDao.insert(new Transaction(null, accountId, subscriptionsCategory,
                    -amounts[i], base.plusMonths(i), "Variable service", false));
        }

        assertFalse(detected("variable service"));
    }

    @Test
    @DisplayName("reference numbers in the description do not split one subscription into many")
    void normalisesReferenceNumbers() {
        assertEquals(SubscriptionService.normalise("NETFLIX 4471"),
                SubscriptionService.normalise("Netflix 8823"));
        assertEquals("netflix", SubscriptionService.normalise("NETFLIX*4471 "));
    }

    @Test
    @DisplayName("a charge that stopped appearing is flagged as abandoned")
    void flagsAbandoned() {
        // Five monthly charges ending four months ago.
        chargeMonthly("Ghost gym membership", 1_500, 8, 5);

        assertTrue(subscriptionService.findCreep().stream()
                .anyMatch(f -> f.subscription().pattern().contains("ghost")
                        && f.reason() == SubscriptionService.CreepReason.ABANDONED));
    }

    @Test
    @DisplayName("a subscription whose price crept up is flagged")
    void flagsPriceIncrease() {
        LocalDate base = LocalDate.now().minusMonths(4).withDayOfMonth(10);
        double[] amounts = {1_000, 1_050, 1_150, 1_250, 1_300};
        for (int i = 0; i < amounts.length; i++) {
            transactionDao.insert(new Transaction(null, accountId, subscriptionsCategory,
                    -amounts[i], base.plusMonths(i), "Creeping service", true));
        }

        assertTrue(subscriptionService.findCreep().stream()
                .anyMatch(f -> f.subscription().pattern().contains("creeping")
                        && f.reason() == SubscriptionService.CreepReason.PRICE_INCREASE));
    }

    @Test
    @DisplayName("a steady, current subscription is not flagged")
    void doesNotFlagHealthySubscription() {
        chargeMonthly("Steady service", 400, 4, 5);

        assertFalse(subscriptionService.findCreep().stream()
                .anyMatch(f -> f.subscription().pattern().contains("steady")));
    }

    @Test
    @DisplayName("detected subscriptions become recurring rules, without duplicating on a re-scan")
    void savesRulesIdempotently() {
        chargeMonthly("Hoichoi subscription", 299, 4, 5);

        int created = subscriptionService.saveDetectedRules();
        assertTrue(created > 0);

        int rulesAfterFirst = ruleDao.findAll().size();
        subscriptionService.saveDetectedRules();

        assertEquals(rulesAfterFirst, ruleDao.findAll().size(),
                "re-scanning must not create duplicate rules");
    }

    @Test
    @DisplayName("a rule the user cancelled is not resurrected by a later scan")
    void respectsUserDecisions() {
        chargeMonthly("Hoichoi subscription", 299, 4, 5);
        subscriptionService.saveDetectedRules();

        RecurringRule rule = ruleDao.findAll().stream()
                .filter(r -> r.getDescriptionPattern().contains("hoichoi"))
                .findFirst().orElseThrow();
        subscriptionService.updateStatus(rule.getId(), RuleStatus.CANCELLED);

        subscriptionService.saveDetectedRules();

        assertEquals(RuleStatus.CANCELLED,
                ruleDao.findById(rule.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("the monthly total sums every detected subscription")
    void monthlyTotal() {
        // The seeded history already contains recurring charges, so the check is
        // on the increase this test causes rather than on an absolute figure.
        double before = subscriptionService.monthlyTotal();

        chargeMonthly("Service one", 300, 4, 5);
        chargeMonthly("Service two", 700, 4, 5);

        assertEquals(before + 1_000, subscriptionService.monthlyTotal(), 1.0);
    }

    @Test
    @DisplayName("the review workflow moves a rule through its statuses")
    void reviewWorkflow() {
        chargeMonthly("Hoichoi subscription", 299, 4, 5);
        subscriptionService.saveDetectedRules();

        RecurringRule rule = ruleDao.findAll().stream()
                .filter(r -> r.getDescriptionPattern().contains("hoichoi"))
                .findFirst().orElseThrow();

        subscriptionService.updateStatus(rule.getId(), RuleStatus.IGNORED);
        List<RecurringRule> ignored = subscriptionService.rulesFor(RuleStatus.IGNORED);

        assertTrue(ignored.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }
}
