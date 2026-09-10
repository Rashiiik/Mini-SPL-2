package com.smartbudget.pattern.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The stack of commands that have run, newest first.
 *
 * <p>Holding executed commands is what turns the pattern into a feature: undo is
 * popping the top and calling {@code undo()} on it. Nothing else in the
 * application needs to remember what was just done.
 *
 * <p>Session-scoped rather than persisted. Undo is for correcting a mistake you
 * just made; offering to reverse something from three launches ago would mean
 * reasoning about whether the data has changed underneath it since.
 */
public class CommandHistory {

    private static final int MAX_HISTORY = 50;

    private final Deque<Command> executed = new ArrayDeque<>();

    /**
     * Runs a command and records it.
     *
     * <p>A command that throws is not recorded, so a failed action can never be
     * "undone" into an inconsistent state.
     */
    public void execute(Command command) {
        command.execute();
        executed.push(command);
        while (executed.size() > MAX_HISTORY) {
            executed.removeLast();
        }
    }

    /** Reverses the most recent command, returning its description if there was one. */
    public Optional<String> undoLast() {
        if (executed.isEmpty()) {
            return Optional.empty();
        }
        Command command = executed.pop();
        command.undo();
        return Optional.of(command.description());
    }

    public boolean canUndo() {
        return !executed.isEmpty();
    }

    public Optional<String> nextUndoDescription() {
        return executed.isEmpty() ? Optional.empty() : Optional.of(executed.peek().description());
    }

    public int size() {
        return executed.size();
    }

    public void clear() {
        executed.clear();
    }
}
