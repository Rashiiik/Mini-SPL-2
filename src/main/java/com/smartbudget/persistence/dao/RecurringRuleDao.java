package com.smartbudget.persistence.dao;

import com.smartbudget.model.Frequency;
import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.RuleStatus;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecurringRuleDao {

    private final Database database;

    public RecurringRuleDao(Database database) {
        this.database = database;
    }

    public RecurringRule insert(RecurringRule rule) {
        String sql = "INSERT INTO recurring_rules "
                + "(description_pattern, category_id, expected_amount, frequency, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, rule);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    rule.setId(keys.getInt(1));
                }
            }
            return rule;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert rule: " + rule.getDescriptionPattern(), e);
        }
    }

    public void update(RecurringRule rule) {
        String sql = "UPDATE recurring_rules SET description_pattern = ?, category_id = ?, "
                + "expected_amount = ?, frequency = ?, status = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            bind(statement, rule);
            statement.setInt(6, rule.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update rule id " + rule.getId(), e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM recurring_rules WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete rule id " + id, e);
        }
    }

    public Optional<RecurringRule> findById(int id) {
        String sql = "SELECT * FROM recurring_rules WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read rule id " + id, e);
        }
    }

    public List<RecurringRule> findAll() {
        return query("SELECT * FROM recurring_rules ORDER BY description_pattern");
    }

    public List<RecurringRule> findByStatus(RuleStatus status) {
        return query("SELECT * FROM recurring_rules WHERE status = ? ORDER BY description_pattern",
                status.dbValue());
    }

    private List<RecurringRule> query(String sql, Object... parameters) {
        List<RecurringRule> rules = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rules.add(map(resultSet));
                }
            }
            return rules;
        } catch (SQLException e) {
            throw new DataAccessException("Could not query recurring rules", e);
        }
    }

    private void bind(PreparedStatement statement, RecurringRule rule) throws SQLException {
        statement.setString(1, rule.getDescriptionPattern());
        if (rule.getCategoryId() == null) {
            statement.setNull(2, java.sql.Types.INTEGER);
        } else {
            statement.setInt(2, rule.getCategoryId());
        }
        statement.setDouble(3, rule.getExpectedAmount());
        statement.setString(4, rule.getFrequency().dbValue());
        statement.setString(5, rule.getStatus().dbValue());
    }

    private RecurringRule map(ResultSet resultSet) throws SQLException {
        Integer categoryId = resultSet.getInt("category_id");
        if (resultSet.wasNull()) {
            categoryId = null;
        }
        return new RecurringRule(
                resultSet.getInt("id"),
                resultSet.getString("description_pattern"),
                categoryId,
                resultSet.getDouble("expected_amount"),
                Frequency.fromDb(resultSet.getString("frequency")),
                RuleStatus.fromDb(resultSet.getString("status")));
    }
}
