package com.smartbudget.persistence;

import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the schema and seeder actually enforce the constraints the rest
 * of the application depends on, rather than accepting anything and failing
 * later in business logic.
 */
class SchemaTest {

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("seeder populates every entity table on a fresh database")
    void seederPopulatesTables() {
        assertFalse(new CategoryDao(database).findAll().isEmpty(), "categories");
        assertFalse(new AccountDao(database).findAll().isEmpty(), "accounts");
        assertFalse(new TransactionDao(database).findAll().isEmpty(), "transactions");
        assertFalse(new BudgetDao(database).findAll().isEmpty(), "budgets");
        assertFalse(new RecurringRuleDao(database).findAll().isEmpty(), "recurring rules");
    }

    @Test
    @DisplayName("reopening a database keeps user data and does not re-run the seeder")
    void dataSurvivesRestartWithoutReseeding(@TempDir Path tempDir) {
        String path = tempDir.resolve("restart-test.db").toString();

        Database first = Database.atPath(path);
        int seededCategories = new CategoryDao(first).findAll().size();
        int seededTransactions = new TransactionDao(first).findAll().size();
        int accountId = new AccountDao(first).findAll().get(0).getId();
        new TransactionDao(first).insert(new Transaction(
                null, accountId, null, -777.0, LocalDate.now(), "added before restart", false));
        first.close();

        Database reopened = Database.atPath(path);
        try {
            assertEquals(seededCategories, new CategoryDao(reopened).findAll().size(),
                    "seeder must not run a second time");
            assertEquals(seededTransactions + 1, new TransactionDao(reopened).findAll().size(),
                    "user data must survive the restart");
            assertTrue(new TransactionDao(reopened).findAll().stream()
                            .anyMatch(t -> "added before restart".equals(t.getDescription())),
                    "the specific row added before closing must still be there");
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("foreign keys are enforced, not merely declared")
    void foreignKeysAreEnforced() {
        String sql = "INSERT INTO transactions (account_id, category_id, amount, date, description) "
                + "VALUES (9999, NULL, -100, '2026-01-01', 'orphan')";
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("a zero-amount transaction is rejected by the CHECK constraint")
    void zeroAmountIsRejected() {
        String sql = "INSERT INTO transactions (account_id, category_id, amount, date, description) "
                + "SELECT id, NULL, 0, '2026-01-01', 'zero' FROM accounts LIMIT 1";
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("an unknown account type is rejected by the CHECK constraint")
    void invalidAccountTypeIsRejected() {
        String sql = "INSERT INTO accounts (name, type, balance) VALUES ('Crypto', 'wallet', 0)";
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("an insight cannot point at both a transaction and a report month")
    void insightSubjectIsExclusive() {
        String sql = "INSERT INTO ai_insights "
                + "(related_transaction_id, related_report_month, kind, generated_text) "
                + "SELECT id, '2026-01', 'anomaly', 'both' FROM transactions LIMIT 1";
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("deleting an account cascades to its transactions")
    void deletingAccountCascades() {
        AccountDao accountDao = new AccountDao(database);
        TransactionDao transactionDao = new TransactionDao(database);

        int accountId = accountDao.findAll().get(0).getId();
        assertFalse(transactionDao.findByAccount(accountId).isEmpty(), "precondition");

        accountDao.delete(accountId);

        assertTrue(transactionDao.findByAccount(accountId).isEmpty());
    }
}
