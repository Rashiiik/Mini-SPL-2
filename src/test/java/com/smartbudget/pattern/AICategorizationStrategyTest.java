package com.smartbudget.pattern;

import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.pattern.strategy.AICategorizationStrategy;
import com.smartbudget.pattern.strategy.CategorizationStrategy;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback behaviour is the point of this class, so it is what gets tested.
 * A stub provider stands in for Groq, which keeps these tests offline and fast
 * — itself a benefit of having put the provider behind an interface.
 */
class AICategorizationStrategyTest {

    private Database database;
    private CategoryDao categoryDao;
    private CategorizationStrategy ruleBased;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        categoryDao = new CategoryDao(database);
        ruleBased = new RuleBasedCategorizationStrategy(categoryDao, new RecurringRuleDao(database));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    /** A provider that always answers with the same fixed text. */
    private AIProvider providerReturning(String answer) {
        return new AIProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<String> complete(String systemPrompt, String userPrompt) {
                return Optional.ofNullable(answer);
            }

            @Override
            public Optional<String> completeWithImage(String prompt, byte[] bytes, String mime) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "Stub";
            }
        };
    }

    private AIProvider providerThatFails() {
        return providerReturning(null);
    }

    private Optional<Category> categorize(CategorizationStrategy strategy, String description) {
        return strategy.categorize(
                new Transaction(null, 1, null, -500, LocalDate.now(), description, false));
    }

    @Test
    @DisplayName("a valid category name from the model is used")
    void usesModelAnswer() {
        CategorizationStrategy strategy =
                new AICategorizationStrategy(providerReturning("Health"), categoryDao, ruleBased);

        assertEquals("Health", categorize(strategy, "Zzyzx Holdings Ltd").orElseThrow().getName());
    }

    @Test
    @DisplayName("quotes, casing and trailing punctuation from the model are tolerated")
    void tolerantOfModelFormatting() {
        assertEquals("Health", categorize(
                new AICategorizationStrategy(providerReturning("\"health\"."), categoryDao, ruleBased),
                "Zzyzx Holdings Ltd").orElseThrow().getName());
    }

    @Test
    @DisplayName("a hallucinated category is rejected and the fallback decides instead")
    void rejectsInventedCategory() {
        CategorizationStrategy strategy = new AICategorizationStrategy(
                providerReturning("Cryptocurrency Speculation"), categoryDao, ruleBased);

        // The keyword rule still recognises this one, so the fallback answers.
        assertEquals("Transport", categorize(strategy, "Uber to office").orElseThrow().getName());
    }

    @Test
    @DisplayName("when the model answers NONE the fallback decides")
    void noneFallsBack() {
        CategorizationStrategy strategy =
                new AICategorizationStrategy(providerReturning("NONE"), categoryDao, ruleBased);

        assertEquals("Groceries", categorize(strategy, "Shwapno grocery run").orElseThrow().getName());
    }

    @Test
    @DisplayName("a failing provider falls back rather than throwing")
    void failureFallsBack() {
        CategorizationStrategy strategy =
                new AICategorizationStrategy(providerThatFails(), categoryDao, ruleBased);

        assertEquals("Transport", categorize(strategy, "Pathao ride").orElseThrow().getName());
    }

    @Test
    @DisplayName("with no API key the provider is never called at all")
    void unavailableProviderSkipsNetwork() {
        AtomicInteger calls = new AtomicInteger();
        AIProvider counting = new AIProvider() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public Optional<String> complete(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return Optional.of("Health");
            }

            @Override
            public Optional<String> completeWithImage(String p, byte[] b, String m) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "Counting";
            }
        };

        CategorizationStrategy strategy =
                new AICategorizationStrategy(counting, categoryDao, ruleBased);

        assertEquals("Transport", categorize(strategy, "Uber to office").orElseThrow().getName());
        assertEquals(0, calls.get(), "an unavailable provider must not be called");
    }

    @Test
    @DisplayName("repeated descriptions are answered from cache, not a second call")
    void cachesByDescription() {
        AtomicInteger calls = new AtomicInteger();
        AIProvider counting = new AIProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<String> complete(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return Optional.of("Health");
            }

            @Override
            public Optional<String> completeWithImage(String p, byte[] b, String m) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "Counting";
            }
        };

        CategorizationStrategy strategy =
                new AICategorizationStrategy(counting, categoryDao, ruleBased);

        categorize(strategy, "Zzyzx Holdings Ltd");
        categorize(strategy, "zzyzx holdings ltd");
        categorize(strategy, "  Zzyzx Holdings Ltd  ");

        assertEquals(1, calls.get(), "the same merchant should cost one call");
    }

    @Test
    @DisplayName("the Null provider leaves the application fully functional")
    void nullProviderIsUsable() {
        CategorizationStrategy strategy =
                new AICategorizationStrategy(new NullAIProvider(), categoryDao, ruleBased);

        assertEquals("Groceries", categorize(strategy, "Meena Bazar weekly").orElseThrow().getName());
        assertTrue(categorize(strategy, "Zzyzx Holdings Ltd").isEmpty());
        assertTrue(strategy.name().contains("AI offline"));
    }

    @Test
    @DisplayName("a blank description never reaches the provider")
    void blankDescriptionShortCircuits() {
        CategorizationStrategy strategy =
                new AICategorizationStrategy(providerReturning("Health"), categoryDao, ruleBased);

        assertTrue(categorize(strategy, "   ").isEmpty());
    }
}
