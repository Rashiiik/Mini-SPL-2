package com.smartbudget.persistence.dao;

import com.smartbudget.model.AIInsight;
import com.smartbudget.model.InsightKind;
import com.smartbudget.model.UserAction;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AIInsightDao {

    /** SQLite's {@code datetime('now')} format, which has a space rather than a 'T'. */
    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Database database;

    public AIInsightDao(Database database) {
        this.database = database;
    }

    public AIInsight insert(AIInsight insight) {
        String sql = "INSERT INTO ai_insights "
                + "(related_transaction_id, related_report_month, kind, generated_text, user_action) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (insight.getRelatedTransactionId() == null) {
                statement.setNull(1, java.sql.Types.INTEGER);
            } else {
                statement.setInt(1, insight.getRelatedTransactionId());
            }
            statement.setString(2, insight.getRelatedReportMonth() == null
                    ? null : insight.getRelatedReportMonth().toString());
            statement.setString(3, insight.getKind().dbValue());
            statement.setString(4, insight.getGeneratedText());
            statement.setString(5, insight.getUserAction().dbValue());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    insight.setId(keys.getInt(1));
                }
            }
            return insight;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert AI insight", e);
        }
    }

    /** Records that the user accepted, edited, or rejected a suggestion. */
    public void updateUserAction(int id, UserAction action) {
        String sql = "UPDATE ai_insights SET user_action = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, action.dbValue());
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update insight id " + id, e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM ai_insights WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete insight id " + id, e);
        }
    }

    public Optional<AIInsight> findByTransaction(int transactionId) {
        String sql = "SELECT * FROM ai_insights WHERE related_transaction_id = ? "
                + "ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read insight for transaction " + transactionId, e);
        }
    }

    public List<AIInsight> findByMonth(YearMonth month) {
        String sql = "SELECT * FROM ai_insights WHERE related_report_month = ? ORDER BY created_at DESC";
        List<AIInsight> insights = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, month.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    insights.add(map(resultSet));
                }
            }
            return insights;
        } catch (SQLException e) {
            throw new DataAccessException("Could not read insights for " + month, e);
        }
    }

    public List<AIInsight> findByKind(InsightKind kind) {
        String sql = "SELECT * FROM ai_insights WHERE kind = ? ORDER BY created_at DESC";
        List<AIInsight> insights = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, kind.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    insights.add(map(resultSet));
                }
            }
            return insights;
        } catch (SQLException e) {
            throw new DataAccessException("Could not read insights of kind " + kind, e);
        }
    }

    private AIInsight map(ResultSet resultSet) throws SQLException {
        AIInsight insight = new AIInsight();
        insight.setId(resultSet.getInt("id"));

        int transactionId = resultSet.getInt("related_transaction_id");
        insight.setRelatedTransactionId(resultSet.wasNull() ? null : transactionId);

        String month = resultSet.getString("related_report_month");
        insight.setRelatedReportMonth(month == null ? null : YearMonth.parse(month));

        insight.setKind(InsightKind.fromDb(resultSet.getString("kind")));
        insight.setGeneratedText(resultSet.getString("generated_text"));
        insight.setUserAction(UserAction.fromDb(resultSet.getString("user_action")));
        insight.setCreatedAt(LocalDateTime.parse(resultSet.getString("created_at"), SQLITE_TIMESTAMP));
        return insight;
    }
}
