package com.smartbudget.pattern.strategy;

import com.smartbudget.model.Category;
import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Categorises using the user's own recurring rules first, then a keyword table.
 *
 * <p>This is the strategy that must never fail: it is both the default and the
 * fallback the AI strategy degrades to, so it performs no network access and
 * throws nothing for an unrecognised description.
 */
public class RuleBasedCategorizationStrategy implements CategorizationStrategy {

    /**
     * Keyword to category-name mapping, checked in insertion order.
     *
     * <p>Ordered deliberately: a description like "uber eats" should match the
     * more specific dining keyword before the transport one, so the more
     * specific entries come first.
     */
    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("uber eats", "Dining");
        KEYWORDS.put("foodpanda", "Dining");
        KEYWORDS.put("restaurant", "Dining");
        KEYWORDS.put("dinner", "Dining");
        KEYWORDS.put("lunch", "Dining");
        KEYWORDS.put("cafe", "Dining");
        KEYWORDS.put("iftar", "Dining");

        KEYWORDS.put("uber", "Transport");
        KEYWORDS.put("pathao", "Transport");
        KEYWORDS.put("cng", "Transport");
        KEYWORDS.put("bus ticket", "Transport");
        KEYWORDS.put("fuel", "Transport");
        KEYWORDS.put("petrol", "Transport");

        KEYWORDS.put("shwapno", "Groceries");
        KEYWORDS.put("meena bazar", "Groceries");
        KEYWORDS.put("bazar", "Groceries");
        KEYWORDS.put("grocery", "Groceries");
        KEYWORDS.put("supermarket", "Groceries");

        KEYWORDS.put("electricity", "Utilities");
        KEYWORDS.put("gas bill", "Utilities");
        KEYWORDS.put("water bill", "Utilities");
        KEYWORDS.put("internet", "Utilities");
        KEYWORDS.put("wifi", "Utilities");

        KEYWORDS.put("rent", "Rent");

        KEYWORDS.put("netflix", "Subscriptions");
        KEYWORDS.put("spotify", "Subscriptions");
        KEYWORDS.put("youtube premium", "Subscriptions");
        KEYWORDS.put("subscription", "Subscriptions");

        KEYWORDS.put("pharmacy", "Health");
        KEYWORDS.put("doctor", "Health");
        KEYWORDS.put("hospital", "Health");
        KEYWORDS.put("medicine", "Health");

        KEYWORDS.put("cinema", "Entertainment");
        KEYWORDS.put("cineplex", "Entertainment");
        KEYWORDS.put("concert", "Entertainment");

        KEYWORDS.put("salary", "Salary");

        KEYWORDS.put("shopping", "Shopping");
        KEYWORDS.put("clothing", "Shopping");
        KEYWORDS.put("jacket", "Shopping");
    }

    private final CategoryDao categoryDao;
    private final RecurringRuleDao ruleDao;

    public RuleBasedCategorizationStrategy(CategoryDao categoryDao, RecurringRuleDao ruleDao) {
        this.categoryDao = categoryDao;
        this.ruleDao = ruleDao;
    }

    @Override
    public Optional<Category> categorize(Transaction transaction) {
        if (transaction == null || transaction.getDescription() == null) {
            return Optional.empty();
        }
        String description = transaction.getDescription();

        // The user's own recurring rules win: they were either confirmed by the
        // user or learned from their actual history, so they beat a generic keyword.
        for (RecurringRule rule : ruleDao.findAll()) {
            if (rule.matches(description) && rule.getCategoryId() != null) {
                Optional<Category> category = categoryDao.findById(rule.getCategoryId());
                if (category.isPresent()) {
                    return category;
                }
            }
        }

        String lowered = description.toLowerCase();
        for (Map.Entry<String, String> entry : KEYWORDS.entrySet()) {
            if (lowered.contains(entry.getKey())) {
                Optional<Category> category = categoryDao.findByName(entry.getValue());
                if (category.isPresent()) {
                    return category;
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public String name() {
        return "Rule-based";
    }
}
