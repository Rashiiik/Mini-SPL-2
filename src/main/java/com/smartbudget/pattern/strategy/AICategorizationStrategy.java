package com.smartbudget.pattern.strategy;

import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.persistence.dao.CategoryDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Categorises with a language model, falling back to another strategy whenever
 * the model is unavailable, slow, or returns something unusable.
 *
 * <p>This is the payoff of the Strategy pattern: it is a new class that
 * {@code TransactionService} consumes through the same interface, and it holds a
 * {@code CategorizationStrategy} of its own as the fallback. Because the
 * fallback is itself just a strategy, the degradation path is ordinary
 * composition rather than a special case.
 *
 * <p>The model is constrained to answering with one name from a supplied list,
 * and any answer outside that list is discarded. A model that invents a category
 * would otherwise create silent gaps in every budget total.
 */
public class AICategorizationStrategy implements CategorizationStrategy {

    private static final String SYSTEM_PROMPT = """
            You categorise personal finance transactions.
            You will be given a transaction description and a list of allowed categories.
            Reply with exactly one category name from the list, copied verbatim.
            If none of them is a reasonable fit, reply with exactly: NONE
            Do not explain. Do not add punctuation. Reply with the name only.""";

    private final AIProvider provider;
    private final CategoryDao categoryDao;
    private final CategorizationStrategy fallback;

    /**
     * Descriptions already resolved this session.
     *
     * <p>Merchant descriptions repeat constantly in personal finance, so caching
     * avoids paying for and waiting on a second call for "Uber to office".
     * Session-scoped on purpose: a user who renames a category should see the
     * change take effect on the next run without a stale answer persisting.
     */
    private final Map<String, String> cache = new HashMap<>();

    public AICategorizationStrategy(AIProvider provider, CategoryDao categoryDao,
                                    CategorizationStrategy fallback) {
        this.provider = provider;
        this.categoryDao = categoryDao;
        this.fallback = fallback;
    }

    @Override
    public Optional<Category> categorize(Transaction transaction) {
        if (transaction == null || transaction.getDescription() == null
                || transaction.getDescription().isBlank()) {
            return fallback.categorize(transaction);
        }
        if (!provider.isAvailable()) {
            return fallback.categorize(transaction);
        }

        String key = transaction.getDescription().trim().toLowerCase();
        if (cache.containsKey(key)) {
            String cached = cache.get(key);
            return cached == null ? fallback.categorize(transaction) : categoryDao.findByName(cached);
        }

        List<Category> categories = categoryDao.findAll();
        if (categories.isEmpty()) {
            return fallback.categorize(transaction);
        }

        String allowed = categories.stream().map(Category::getName).reduce((a, b) -> a + ", " + b).orElse("");
        String userPrompt = "Allowed categories: " + allowed
                + "\nTransaction description: " + transaction.getDescription()
                + "\nDirection: " + (transaction.isExpense() ? "money spent" : "money received");

        Optional<Category> resolved = provider.complete(SYSTEM_PROMPT, userPrompt)
                .flatMap(answer -> matchToCategory(answer, categories));

        cache.put(key, resolved.map(Category::getName).orElse(null));

        // An empty result here covers every failure mode at once: no key, a
        // timeout, a rate limit, or a hallucinated category name.
        return resolved.isPresent() ? resolved : fallback.categorize(transaction);
    }

    /**
     * Accepts the model's answer only if it names a real category.
     *
     * <p>Tolerates surrounding quotes, trailing full stops and case differences,
     * because those are the model's habits rather than genuine disagreement —
     * but never accepts a name that does not exist.
     */
    private Optional<Category> matchToCategory(String answer, List<Category> categories) {
        String cleaned = answer.trim()
                .replaceAll("^[\"'`]+", "")
                .replaceAll("[\"'`.]+$", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("NONE")) {
            return Optional.empty();
        }
        for (Category category : categories) {
            if (category.getName().equalsIgnoreCase(cleaned)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    @Override
    public String name() {
        return provider.isAvailable() ? "AI (" + provider.name() + ")" : fallback.name() + " (AI offline)";
    }
}
