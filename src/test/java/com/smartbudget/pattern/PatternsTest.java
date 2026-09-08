package com.smartbudget.pattern;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.factory.AccountFactory;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.observer.BudgetListener;
import com.smartbudget.pattern.strategy.CategorizationStrategy;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternsTest {

    @Nested
    @DisplayName("Strategy: rule-based categorisation")
    class StrategyTest {

        private Database database;
        private CategorizationStrategy strategy;

        @BeforeEach
        void setUp() {
            database = Database.inMemory();
            strategy = new RuleBasedCategorizationStrategy(
                    new CategoryDao(database), new RecurringRuleDao(database));
        }

        @AfterEach
        void tearDown() {
            database.close();
        }

        private Optional<Category> categorize(String description) {
            return strategy.categorize(new Transaction(
                    null, 1, null, -100, LocalDate.now(), description, false));
        }

        @Test
        @DisplayName("a seeded recurring rule takes priority")
        void recurringRuleMatches() {
            assertEquals("Subscriptions", categorize("Netflix subscription").orElseThrow().getName());
        }

        @Test
        @DisplayName("falls back to keywords when no rule matches")
        void keywordFallback() {
            assertEquals("Transport", categorize("Pathao ride home").orElseThrow().getName());
            assertEquals("Groceries", categorize("Weekly grocery shop").orElseThrow().getName());
            assertEquals("Health", categorize("Pharmacy visit").orElseThrow().getName());
        }

        @Test
        @DisplayName("matching ignores case")
        void caseInsensitive() {
            assertEquals("Transport", categorize("PATHAO RIDE").orElseThrow().getName());
        }

        @Test
        @DisplayName("more specific keywords win over more general ones")
        void specificKeywordWins() {
            assertEquals("Dining", categorize("Uber Eats order").orElseThrow().getName());
            assertEquals("Transport", categorize("Uber to office").orElseThrow().getName());
        }

        @Test
        @DisplayName("an unknown merchant yields no category rather than a guess")
        void unknownYieldsEmpty() {
            assertTrue(categorize("Zzyzx Holdings Ltd").isEmpty());
        }

        @Test
        @DisplayName("a null description is handled without throwing")
        void nullDescriptionIsSafe() {
            assertTrue(strategy.categorize(
                    new Transaction(null, 1, null, -100, LocalDate.now(), null, false)).isEmpty());
            assertTrue(strategy.categorize(null).isEmpty());
        }

        @Test
        @DisplayName("the strategy reports its name for display")
        void reportsName() {
            assertEquals("Rule-based", strategy.name());
        }
    }

    @Nested
    @DisplayName("Factory: account creation")
    class FactoryTest {

        private final AccountFactory factory = new AccountFactory();

        @Test
        @DisplayName("each type is created with its own initial state")
        void typeDefaults() {
            Account chequing = factory.create(AccountType.CHECKING, "Salary Account", 5_000, "BDT");
            assertEquals(AccountType.CHECKING, chequing.getType());
            assertEquals(5_000, chequing.getBalance(), 0.001);

            Account credit = factory.create(AccountType.CREDIT, "Visa", 2_000, "BDT");
            assertEquals(2_000, credit.getBalance(), 0.001, "a credit balance is debt owed");

            Account empty = factory.create(AccountType.SAVINGS, "New Savings");
            assertEquals(0, empty.getBalance(), 0.001);
        }

        @Test
        @DisplayName("currency defaults and is normalised to upper case")
        void currencyHandling() {
            assertEquals("BDT", factory.create(AccountType.SAVINGS, "A", 0, null).getCurrency());
            assertEquals("USD", factory.create(AccountType.SAVINGS, "B", 0, "usd").getCurrency());
        }

        @Test
        @DisplayName("the account name is trimmed")
        void nameIsTrimmed() {
            assertEquals("My Wallet",
                    factory.create(AccountType.CHECKING, "  My Wallet  ", 0, "BDT").getName());
        }

        @Test
        @DisplayName("invalid input is rejected with a message meant for the user")
        void rejectsInvalidInput() {
            assertThrows(ValidationException.class, () -> factory.create(null, "X", 0, "BDT"));
            assertThrows(ValidationException.class, () -> factory.create(AccountType.SAVINGS, " ", 0, "BDT"));
            assertThrows(ValidationException.class, () -> factory.create(AccountType.SAVINGS, "X", -1, "BDT"));
            assertThrows(ValidationException.class, () -> factory.create(AccountType.CREDIT, "X", -1, "BDT"));
            assertThrows(ValidationException.class, () -> factory.create(AccountType.SAVINGS, "X", 0, "TAKA"));
        }
    }

    @Nested
    @DisplayName("Observer: budget event bus")
    class ObserverTest {

        private final BudgetEventBus bus = new BudgetEventBus();

        private BudgetAlert anyAlert() {
            return new BudgetAlert("Dining", YearMonth.now(), 1_200, 1_000, AlertSeverity.EXCEEDED);
        }

        @Test
        @DisplayName("every subscriber receives a published alert")
        void publishesToAll() {
            List<BudgetAlert> first = new ArrayList<>();
            List<BudgetAlert> second = new ArrayList<>();
            bus.subscribe(first::add);
            bus.subscribe(second::add);

            bus.publish(anyAlert());

            assertEquals(1, first.size());
            assertEquals(1, second.size());
        }

        @Test
        @DisplayName("an unsubscribed listener stops receiving alerts")
        void unsubscribeStopsDelivery() {
            List<BudgetAlert> received = new ArrayList<>();
            BudgetListener listener = received::add;
            bus.subscribe(listener);
            bus.unsubscribe(listener);

            bus.publish(anyAlert());

            assertTrue(received.isEmpty());
        }

        @Test
        @DisplayName("the same listener is not registered twice")
        void subscribeIsIdempotent() {
            BudgetListener listener = alert -> { };
            bus.subscribe(listener);
            bus.subscribe(listener);

            assertEquals(1, bus.listenerCount());
        }

        @Test
        @DisplayName("a listener may unsubscribe itself while being notified")
        void listenerCanUnsubscribeDuringDispatch() {
            List<BudgetAlert> received = new ArrayList<>();
            BudgetListener selfRemoving = new BudgetListener() {
                @Override
                public void onBudgetAlert(BudgetAlert alert) {
                    received.add(alert);
                    bus.unsubscribe(this);
                }
            };
            bus.subscribe(selfRemoving);

            bus.publish(anyAlert());
            bus.publish(anyAlert());

            assertEquals(1, received.size());
            assertEquals(0, bus.listenerCount());
        }

        @Test
        @DisplayName("publishing with no subscribers is harmless")
        void publishWithNoListeners() {
            bus.publish(anyAlert());
            assertEquals(0, bus.listenerCount());
        }

        @Test
        @DisplayName("severity is derived from utilisation")
        void severityThresholds() {
            assertEquals(AlertSeverity.OK, AlertSeverity.forUtilisation(0.50));
            assertEquals(AlertSeverity.OK, AlertSeverity.forUtilisation(0.79));
            assertEquals(AlertSeverity.WARNING, AlertSeverity.forUtilisation(0.80));
            assertEquals(AlertSeverity.WARNING, AlertSeverity.forUtilisation(1.00));
            assertEquals(AlertSeverity.EXCEEDED, AlertSeverity.forUtilisation(1.01));
        }

        @Test
        @DisplayName("the alert composes its own message so listeners phrase it alike")
        void alertMessage() {
            BudgetAlert alert = anyAlert();
            assertTrue(alert.message().contains("Dining"));
            assertTrue(alert.message().contains("over budget"));
            assertFalse(alert.message().isBlank());
        }
    }
}
