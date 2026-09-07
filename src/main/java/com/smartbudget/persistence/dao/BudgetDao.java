package com.smartbudget.persistence.dao;

import com.smartbudget.model.Budget;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BudgetDao {

    private final Database database;

    public BudgetDao(Database database) {
        this.database = database;
    }

    /**
     * Inserts a target, or replaces the existing one for that category and month.
     *
     * <p>Relies on the {@code UNIQUE (category_id, month)} constraint: "set the
     * budget for Groceries in March" is naturally an upsert, and expressing it
     * as one lets the caller avoid an exists-check round trip.
     */
    public Budget save(Budget budget) {
        String sql = "INSERT INTO budgets (category_id, month, target_amount) VALUES (?, ?, ?) "
                + "ON CONFLICT (category_id, month) DO UPDATE SET target_amount = excluded.target_amount";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, budget.getCategoryId());
            statement.setString(2, budget.getMonth().toString());
            statement.setDouble(3, budget.getTargetAmount());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    budget.setId(keys.getInt(1));
                }
            }
            return budget;
        } catch (SQLException e) {
            throw new DataAccessException("Could not save budget for month " + budget.getMonth(), e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM budgets WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete budget id " + id, e);
        }
    }

    public Optional<Budget> findByCategoryAndMonth(int categoryId, YearMonth month) {
        String sql = "SELECT * FROM budgets WHERE category_id = ? AND month = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            statement.setString(2, month.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read budget for category " + categoryId, e);
        }
    }

    public List<Budget> findByMonth(YearMonth month) {
        String sql = "SELECT * FROM budgets WHERE month = ? ORDER BY category_id";
        List<Budget> budgets = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, month.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    budgets.add(map(resultSet));
                }
            }
            return budgets;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list budgets for " + month, e);
        }
    }

    public List<Budget> findAll() {
        List<Budget> budgets = new ArrayList<>();
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("SELECT * FROM budgets ORDER BY month DESC");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                budgets.add(map(resultSet));
            }
            return budgets;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list budgets", e);
        }
    }

    private Budget map(ResultSet resultSet) throws SQLException {
        return new Budget(
                resultSet.getInt("id"),
                resultSet.getInt("category_id"),
                YearMonth.parse(resultSet.getString("month")),
                resultSet.getDouble("target_amount"));
    }
}
