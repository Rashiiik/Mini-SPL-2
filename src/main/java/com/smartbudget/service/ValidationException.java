package com.smartbudget.service;

/**
 * Thrown when input fails a business rule.
 *
 * <p>Separate from {@link com.smartbudget.persistence.DataAccessException}: this
 * one carries a message written for the user and is expected during normal use,
 * so the UI shows it in a dialog rather than treating it as a crash.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
