package com.smartbudget.pattern.command;

/**
 * An action the user asked for, packaged as an object that can also reverse itself.
 *
 * <p><b>Problem it solves:</b> free-text entry turns one typed sentence into a
 * validated change to the database. That change needs to be reversible, because
 * a parser working from natural language will sometimes read "450" as the amount
 * when the user meant something else, and the mistake is only obvious once the
 * transaction appears in the table.
 *
 * <p><b>Why Command:</b> it makes the requested action a first-class object, so
 * it can be held, described and undone after the fact. A history of executed
 * commands gives undo for free, and would give a redo stack or an audit log with
 * no change to the callers.
 *
 * <p><b>Alternative considered:</b> having the parser call
 * {@code TransactionService.create} directly. Simpler by one class, but there is
 * then nothing to hold on to: undo would mean the UI separately remembering
 * which row it just inserted, which is exactly the bookkeeping this pattern
 * exists to own.
 *
 * <p>A Command with no {@link #undo()} would be an ordinary method call wearing a
 * class as a costume. The undo is what earns the pattern its place here.
 */
public interface Command {

    void execute();

    void undo();

    /** Short description of what this command did, for the UI. */
    String description();

    /** Whether this command has run and not yet been undone. */
    boolean isExecuted();
}
