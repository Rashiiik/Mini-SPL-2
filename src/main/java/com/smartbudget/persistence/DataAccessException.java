package com.smartbudget.persistence;

/**
 * Wraps {@link java.sql.SQLException} so the service and UI layers never import
 * {@code java.sql}. Keeping JDBC types behind the persistence boundary is what
 * allows the storage mechanism to be swapped later without touching callers.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
