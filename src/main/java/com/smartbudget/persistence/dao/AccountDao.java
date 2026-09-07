package com.smartbudget.persistence.dao;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDao {

    private final Database database;

    public AccountDao(Database database) {
        this.database = database;
    }

    public Account insert(Account account) {
        String sql = "INSERT INTO accounts (name, type, balance, currency) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, account.getName());
            statement.setString(2, account.getType().dbValue());
            statement.setDouble(3, account.getBalance());
            statement.setString(4, account.getCurrency());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    account.setId(keys.getInt(1));
                }
            }
            return account;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert account: " + account.getName(), e);
        }
    }

    public void update(Account account) {
        String sql = "UPDATE accounts SET name = ?, type = ?, balance = ?, currency = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, account.getName());
            statement.setString(2, account.getType().dbValue());
            statement.setDouble(3, account.getBalance());
            statement.setString(4, account.getCurrency());
            statement.setInt(5, account.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update account id " + account.getId(), e);
        }
    }

    /**
     * Applies a signed delta to the stored balance in a single statement.
     *
     * <p>Done in SQL rather than read-modify-write in Java so the balance cannot
     * drift if two updates interleave.
     */
    public void adjustBalance(int accountId, double delta) {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setDouble(1, delta);
            statement.setInt(2, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not adjust balance for account id " + accountId, e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete account id " + id, e);
        }
    }

    public Optional<Account> findById(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read account id " + id, e);
        }
    }

    public List<Account> findAll() {
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("SELECT * FROM accounts ORDER BY name");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(map(resultSet));
            }
            return accounts;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list accounts", e);
        }
    }

    private Account map(ResultSet resultSet) throws SQLException {
        return new Account(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                AccountType.fromDb(resultSet.getString("type")),
                resultSet.getDouble("balance"),
                resultSet.getString("currency"));
    }
}
