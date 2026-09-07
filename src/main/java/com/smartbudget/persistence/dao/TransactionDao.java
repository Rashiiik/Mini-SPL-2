package com.smartbudget.persistence.dao;

import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransactionDao {

    private final Database database;

    public TransactionDao(Database database) {
        this.database = database;
    }

    public Transaction insert(Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(account_id, category_id, amount, date, description, is_recurring) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, transaction);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    transaction.setId(keys.getInt(1));
                }
            }
            return transaction;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert transaction: " + transaction.getDescription(), e);
        }
    }

    public void update(Transaction transaction) {
        String sql = "UPDATE transactions SET account_id = ?, category_id = ?, amount = ?, "
                + "date = ?, description = ?, is_recurring = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            bind(statement, transaction);
            statement.setInt(7, transaction.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update transaction id " + transaction.getId(), e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM transactions WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete transaction id " + id, e);
        }
    }

    public Optional<Transaction> findById(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read transaction id " + id, e);
        }
    }

    public List<Transaction> findAll() {
        return query("SELECT * FROM transactions ORDER BY date DESC, id DESC");
    }

    public List<Transaction> findByAccount(int accountId) {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY date DESC, id DESC";
        return query(sql, accountId);
    }

    public List<Transaction> findByMonth(YearMonth month) {
        String sql = "SELECT * FROM transactions WHERE strftime('%Y-%m', date) = ? "
                + "ORDER BY date DESC, id DESC";
        return query(sql, month.toString());
    }

    public List<Transaction> findByCategoryAndMonth(int categoryId, YearMonth month) {
        String sql = "SELECT * FROM transactions WHERE category_id = ? "
                + "AND strftime('%Y-%m', date) = ? ORDER BY date DESC, id DESC";
        return query(sql, categoryId, month.toString());
    }

    public List<Transaction> findBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM transactions WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC";
        return query(sql, from.toString(), to.toString());
    }

    /**
     * Total expense (as a positive number) for one category in one month.
     *
     * <p>Aggregated in SQL rather than by summing in Java, so the reporting
     * screens stay responsive as the transaction table grows.
     */
    public double sumExpensesByCategoryAndMonth(int categoryId, YearMonth month) {
        String sql = "SELECT COALESCE(SUM(-amount), 0) FROM transactions "
                + "WHERE category_id = ? AND amount < 0 AND strftime('%Y-%m', date) = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            statement.setString(2, month.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not total category " + categoryId + " for " + month, e);
        }
    }

    /**
     * Spending breakdown for a month: category name to positive total.
     * One of the two required analytical operations.
     */
    public Map<String, Double> spendingByCategory(YearMonth month) {
        String sql = "SELECT COALESCE(c.name, 'Uncategorised') AS name, SUM(-t.amount) AS total "
                + "FROM transactions t LEFT JOIN categories c ON c.id = t.category_id "
                + "WHERE t.amount < 0 AND strftime('%Y-%m', t.date) = ? "
                + "GROUP BY name ORDER BY total DESC";
        Map<String, Double> breakdown = new LinkedHashMap<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, month.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    breakdown.put(resultSet.getString("name"), resultSet.getDouble("total"));
                }
            }
            return breakdown;
        } catch (SQLException e) {
            throw new DataAccessException("Could not build spending breakdown for " + month, e);
        }
    }

    private List<Transaction> query(String sql, Object... parameters) {
        List<Transaction> transactions = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(map(resultSet));
                }
            }
            return transactions;
        } catch (SQLException e) {
            throw new DataAccessException("Could not query transactions", e);
        }
    }

    private void bind(PreparedStatement statement, Transaction transaction) throws SQLException {
        statement.setInt(1, transaction.getAccountId());
        if (transaction.getCategoryId() == null) {
            statement.setNull(2, java.sql.Types.INTEGER);
        } else {
            statement.setInt(2, transaction.getCategoryId());
        }
        statement.setDouble(3, transaction.getAmount());
        statement.setString(4, transaction.getDate().toString());
        statement.setString(5, transaction.getDescription());
        statement.setInt(6, transaction.isRecurring() ? 1 : 0);
    }

    private Transaction map(ResultSet resultSet) throws SQLException {
        Integer categoryId = resultSet.getInt("category_id");
        if (resultSet.wasNull()) {
            categoryId = null;
        }
        return new Transaction(
                resultSet.getInt("id"),
                resultSet.getInt("account_id"),
                categoryId,
                resultSet.getDouble("amount"),
                LocalDate.parse(resultSet.getString("date")),
                resultSet.getString("description"),
                resultSet.getInt("is_recurring") == 1);
    }
}
