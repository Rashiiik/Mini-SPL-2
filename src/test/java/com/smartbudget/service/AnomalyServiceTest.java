package com.smartbudget.service;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Category;
import com.smartbudget.model.CategoryType;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.TransactionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection is arithmetic, so it is fully testable offline — which is the whole
 * argument for keeping the statistics out of the model.
 */
class AnomalyServiceTest {

    private Database database;
    private TransactionDao transactionDao;
    private AnomalyService anomalyService;
    private int accountId;
    private int categoryId;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        transactionDao = new TransactionDao(database);
        CategoryDao categoryDao = new CategoryDao(database);
        AIInsightDao insightDao = new AIInsightDao(database);

        anomalyService = new AnomalyService(
                transactionDao, categoryDao, insightDao, new NullAIProvider());

        accountId = new AccountDao(database).insert(
                new Account(null, "Anomaly Test", AccountType.CHECKING, 500_000, "BDT")).getId();
        categoryId = categoryDao.insert(
                new Category(null, "Test Outliers", CategoryType.EXPENSE)).getId();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private Transaction spend(double amount, int dayOfMonth) {
        return transactionDao.insert(new Transaction(null, accountId, categoryId, -amount,
                LocalDate.now().withDayOfMonth(dayOfMonth), "Spend " + amount, false));
    }

    private void seedRoutineSpending() {
        spend(1_000, 1);
        spend(1_100, 2);
        spend(950, 3);
        spend(1_050, 4);
        spend(1_000, 5);
    }

    @Test
    @DisplayName("a large outlier against a steady history is flagged")
    void flagsOutlier() {
        seedRoutineSpending();
        Transaction outlier = spend(15_000, 6);

        Optional<AnomalyService.AnomalyFinding> finding = anomalyService.analyse(outlier);

        assertTrue(finding.isPresent());
        assertEquals(15_000, finding.get().amount(), 0.001);
        assertEquals(5, finding.get().sampleSize());
    }

    @Test
    @DisplayName("an ordinary transaction is not flagged")
    void ignoresNormalSpending() {
        seedRoutineSpending();
        Transaction normal = spend(1_020, 6);

        assertTrue(anomalyService.analyse(normal).isEmpty());
    }

    @Test
    @DisplayName("too little history means no judgement is made")
    void refusesToJudgeThinHistory() {
        spend(1_000, 1);
        spend(1_100, 2);
        Transaction outlier = spend(20_000, 3);

        assertTrue(anomalyService.analyse(outlier).isEmpty(),
                "three transactions is not enough history to call something unusual");
    }

    @Test
    @DisplayName("trivial amounts are never flagged, however unusual")
    void ignoresTrivialAmounts() {
        for (int day = 1; day <= 5; day++) {
            spend(10, day);
        }
        Transaction smallOutlier = spend(400, 6);

        assertTrue(anomalyService.analyse(smallOutlier).isEmpty());
    }

    @Test
    @DisplayName("income is never flagged as unusual spending")
    void ignoresIncome() {
        seedRoutineSpending();
        Transaction income = transactionDao.insert(new Transaction(null, accountId, categoryId,
                50_000, LocalDate.now().withDayOfMonth(6), "Bonus", false));

        assertTrue(anomalyService.analyse(income).isEmpty());
    }

    @Test
    @DisplayName("an uncategorised transaction is not judged, having no history to compare against")
    void ignoresUncategorised() {
        seedRoutineSpending();
        Transaction uncategorised = transactionDao.insert(new Transaction(null, accountId, null,
                -20_000, LocalDate.now().withDayOfMonth(6), "Mystery", false));

        assertTrue(anomalyService.analyse(uncategorised).isEmpty());
    }

    @Test
    @DisplayName("without AI, the explanation is still complete and quotes the real figures")
    void explanationWorksOffline() {
        seedRoutineSpending();
        AnomalyService.AnomalyFinding finding = anomalyService.analyse(spend(15_000, 6)).orElseThrow();

        String explanation = anomalyService.explain(finding);

        assertFalse(explanation.isBlank());
        assertTrue(explanation.contains("15000") || explanation.contains("15,000.00")
                        || explanation.contains("15000.00"),
                "the offline explanation should state the actual amount: " + explanation);
    }

    @Test
    @DisplayName("the AI explanation is used when a provider answers")
    void explanationUsesProviderWhenAvailable() {
        seedRoutineSpending();
        AIProvider stub = new AIProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<String> complete(String systemPrompt, String userPrompt) {
                return Optional.of("That dinner cost far more than your usual meals.");
            }

            @Override
            public Optional<String> completeWithImage(String p, byte[] b, String m) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "Stub";
            }
        };
        AnomalyService withAi = new AnomalyService(transactionDao,
                new CategoryDao(database), new AIInsightDao(database), stub);

        AnomalyService.AnomalyFinding finding = withAi.analyse(spend(15_000, 6)).orElseThrow();

        assertEquals("That dinner cost far more than your usual meals.", withAi.explain(finding));
    }

    @Test
    @DisplayName("scanning a month stores an insight per outlier, and re-scanning does not duplicate")
    void scanIsRepeatable() {
        seedRoutineSpending();
        Transaction outlier = spend(15_000, 6);

        // The seeded history contains its own outlier, so the assertion is that
        // this one is among the findings rather than that it is the only one.
        assertTrue(anomalyService.scanMonth(YearMonth.now()).stream()
                        .anyMatch(f -> f.transaction().getId().equals(outlier.getId())),
                "the planted outlier should be found");

        int afterFirst = anomalyService.storedAnomalies().size();

        anomalyService.scanMonth(YearMonth.now());

        assertEquals(afterFirst, anomalyService.storedAnomalies().size(),
                "re-scanning must not create a second insight for the same transaction");
    }
}
