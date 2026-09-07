package com.smartbudget.persistence.dao;

import com.smartbudget.model.Category;
import com.smartbudget.model.CategoryType;
import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.persistence.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryDao {

    private final Database database;

    public CategoryDao(Database database) {
        this.database = database;
    }

    public Category insert(Category category) {
        String sql = "INSERT INTO categories (name, type) VALUES (?, ?)";
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getType().dbValue());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getInt(1));
                }
            }
            return category;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert category: " + category.getName(), e);
        }
    }

    public void update(Category category) {
        String sql = "UPDATE categories SET name = ?, type = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getType().dbValue());
            statement.setInt(3, category.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update category id " + category.getId(), e);
        }
    }

    public void delete(int id) {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement("DELETE FROM categories WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete category id " + id, e);
        }
    }

    public Optional<Category> findById(int id) {
        return findOne("SELECT * FROM categories WHERE id = ?", id);
    }

    public Optional<Category> findByName(String name) {
        String sql = "SELECT * FROM categories WHERE name = ? COLLATE NOCASE";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not look up category: " + name, e);
        }
    }

    public List<Category> findAll() {
        String sql = "SELECT * FROM categories ORDER BY type, name";
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(map(resultSet));
            }
            return categories;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list categories", e);
        }
    }

    public List<Category> findByType(CategoryType type) {
        String sql = "SELECT * FROM categories WHERE type = ? ORDER BY name";
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, type.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    categories.add(map(resultSet));
                }
            }
            return categories;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list categories of type " + type, e);
        }
    }

    private Optional<Category> findOne(String sql, int id) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read category id " + id, e);
        }
    }

    private Category map(ResultSet resultSet) throws SQLException {
        return new Category(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                CategoryType.fromDb(resultSet.getString("type")));
    }
}
