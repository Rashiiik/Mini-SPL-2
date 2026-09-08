package com.smartbudget.service;

import com.smartbudget.model.AIInsight;
import com.smartbudget.model.Category;
import com.smartbudget.model.InsightKind;
import com.smartbudget.model.Transaction;
import com.smartbudget.model.UserAction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.TransactionDao;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flags unusual spending, and explains why.
 *
 * <p>The decision is statistical, not AI. A transaction is compared against the
 * user's own history in that category, and only if the numbers say it is an
 * outlier is the model asked for a sentence of explanation. Two reasons: the
 * feature keeps working with no network, and asking a model "is 14,500 unusual
 * for dining?" without the history would produce confident guesses rather than
 * answers grounded in this user's actual behaviour.
 */
public class AnomalyService {

    /** How far above the mean, in standard deviations, counts as unusual. */
    private static final double SIGMA_THRESHOLD = 2.0;

    /** Fallback rule for small or tightly-clustered samples. */
    private static final double MEDIAN_MULTIPLE = 3.0;

    /** Below this many prior transactions the history is too thin to judge. */
    private static final int MINIMUM_SAMPLE = 4;

    /** Ignore trivial amounts, so a 30-taka outlier never raises an alert. */
    private static final double MINIMUM_AMOUNT = 500.0;

    private static final String SYSTEM_PROMPT = """
            You explain unusual personal spending to the person who spent it.
            Write one short sentence, at most 25 words, in plain language.
            State what makes this transaction unusual compared with their usual
            spending in that category. Do not give advice, do not moralise, and
            do not invent facts beyond the numbers provided.""";

    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;
    private final AIInsightDao insightDao;
    private final AIProvider aiProvider;

    public AnomalyService(TransactionDao transactionDao, CategoryDao categoryDao,
                          AIInsightDao insightDao, AIProvider aiProvider) {
        this.transactionDao = transactionDao;
        this.categoryDao = categoryDao;
        this.insightDao = insightDao;
        this.aiProvider = aiProvider;
    }

    /**
     * Decides whether one transaction is an outlier against its own category's
     * history, without touching the network.
     */
    public Optional<AnomalyFinding> analyse(Transaction transaction) {
        if (transaction == null || !transaction.isExpense()
                || transaction.getCategoryId() == null
                || transaction.getAbsoluteAmount() < MINIMUM_AMOUNT) {
            return Optional.empty();
        }

        List<Double> history = historyFor(transaction);
        if (history.size() < MINIMUM_SAMPLE) {
            return Optional.empty();
        }

        double amount = transaction.getAbsoluteAmount();
        double mean = mean(history);
        double deviation = standardDeviation(history, mean);
        double median = median(history);

        // Two rules, because either alone misses real cases: sigma fails when
        // the history is tightly clustered (a tiny deviation flags everything),
        // and the median multiple fails when spending is naturally spread out.
        boolean sigmaOutlier = deviation > 0 && amount > mean + SIGMA_THRESHOLD * deviation;
        boolean medianOutlier = median > 0 && amount > MEDIAN_MULTIPLE * median;
        if (!sigmaOutlier && !medianOutlier) {
            return Optional.empty();
        }

        String categoryName = categoryDao.findById(transaction.getCategoryId())
                .map(Category::getName)
                .orElse("Uncategorised");

        return Optional.of(new AnomalyFinding(
                transaction, categoryName, amount, mean, median, history.size(),
                deviation > 0 ? (amount - mean) / deviation : 0));
    }

    /**
     * Produces the human-readable explanation for a finding.
     *
     * <p>Asks the model first, and falls back to a deterministic sentence built
     * from the same numbers. The fallback is not a degraded placeholder — it is
     * a complete, accurate explanation, which is why the feature is honest to
     * demonstrate with the network switched off.
     */
    public String explain(AnomalyFinding finding) {
        String userPrompt = String.format(
                "Category: %s%nThis transaction: %s, amount %.2f, dated %s%n"
                        + "Their usual spending in this category: average %.2f, median %.2f, "
                        + "based on %d previous transactions.",
                finding.categoryName(), finding.transaction().getDescription(),
                finding.amount(), finding.transaction().getDate(),
                finding.mean(), finding.median(), finding.sampleSize());

        return aiProvider.complete(SYSTEM_PROMPT, userPrompt)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElseGet(() -> deterministicExplanation(finding));
    }

    private String deterministicExplanation(AnomalyFinding finding) {
        double multiple = finding.median() > 0 ? finding.amount() / finding.median() : 0;
        return String.format(
                "This %s charge of %.2f is about %.1f times your usual %.2f in this category, "
                        + "based on %d previous transactions.",
                finding.categoryName(), finding.amount(), multiple,
                finding.median(), finding.sampleSize());
    }

    /**
     * Scans a month, storing an explained insight for each outlier found.
     * Existing insights are not duplicated, so the scan can be re-run safely.
     */
    public List<AnomalyFinding> scanMonth(YearMonth month) {
        List<AnomalyFinding> findings = new ArrayList<>();
        for (Transaction transaction : transactionDao.findByMonth(month)) {
            analyse(transaction).ifPresent(finding -> {
                findings.add(finding);
                if (insightDao.findByTransaction(transaction.getId()).isEmpty()) {
                    insightDao.insert(AIInsight.forTransaction(
                            transaction.getId(), InsightKind.ANOMALY, explain(finding)));
                }
            });
        }
        return findings;
    }

    public List<AIInsight> storedAnomalies() {
        return insightDao.findByKind(InsightKind.ANOMALY);
    }

    public void recordUserDecision(int insightId, UserAction action) {
        insightDao.updateUserAction(insightId, action);
    }

    /** Prior expenses in the same category, excluding the transaction being judged. */
    private List<Double> historyFor(Transaction transaction) {
        List<Double> history = new ArrayList<>();
        for (Transaction other : transactionDao.findAll()) {
            boolean sameCategory = transaction.getCategoryId().equals(other.getCategoryId());
            boolean isSelf = transaction.getId() != null && transaction.getId().equals(other.getId());
            if (sameCategory && other.isExpense() && !isSelf) {
                history.add(other.getAbsoluteAmount());
            }
        }
        return history;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double standardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0;
        }
        double sumOfSquares = values.stream()
                .mapToDouble(value -> (value - mean) * (value - mean))
                .sum();
        // Sample standard deviation: this is a sample of the user's spending,
        // not the complete population of everything they will ever spend.
        return Math.sqrt(sumOfSquares / (values.size() - 1));
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        if (size == 0) {
            return 0;
        }
        return size % 2 == 1
                ? sorted.get(size / 2)
                : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    /** One outlier, with the statistics that justified flagging it. */
    public record AnomalyFinding(
            Transaction transaction,
            String categoryName,
            double amount,
            double mean,
            double median,
            int sampleSize,
            double sigmaDistance) {
    }
}
