package com.smartbudget.pattern.command;

import com.smartbudget.model.Transaction;
import com.smartbudget.service.TransactionService;
import com.smartbudget.service.ValidationException;

/**
 * Adds a transaction, and can take it back out again.
 *
 * <p>Undo deletes through {@link TransactionService#delete(int)} rather than the
 * DAO, so the account balance is reversed by the same logic that applied it.
 * Deleting the row directly would leave the balance permanently wrong.
 */
public class AddTransactionCommand implements Command {

    private final TransactionService transactionService;
    private final Transaction transaction;

    private Integer savedId;

    public AddTransactionCommand(TransactionService transactionService, Transaction transaction) {
        this.transactionService = transactionService;
        this.transaction = transaction;
    }

    @Override
    public void execute() {
        if (savedId != null) {
            throw new ValidationException("This transaction has already been added.");
        }
        savedId = transactionService.create(transaction).getId();
    }

    @Override
    public void undo() {
        if (savedId == null) {
            return;
        }
        transactionService.delete(savedId);
        savedId = null;
        // Clear the id so the command can be executed again cleanly, which is
        // what a redo stack would need.
        transaction.setId(null);
    }

    @Override
    public boolean isExecuted() {
        return savedId != null;
    }

    @Override
    public String description() {
        return String.format("Add \"%s\" for %.2f on %s",
                transaction.getDescription(), transaction.getAmount(), transaction.getDate());
    }

    public Transaction transaction() {
        return transaction;
    }
}
