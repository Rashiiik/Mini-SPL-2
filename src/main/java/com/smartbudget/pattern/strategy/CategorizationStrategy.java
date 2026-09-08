package com.smartbudget.pattern.strategy;

import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;

import java.util.Optional;

/**
 * Decides which category a transaction belongs to.
 *
 * <p><b>Problem it solves:</b> categorisation has more than one plausible
 * implementation — keyword and recurring-rule matching, an AI model, and later
 * possibly a local model or a learned classifier. Encoding the choice as an
 * {@code if} inside {@code TransactionService} would mean that service is
 * edited and re-tested every time a new method is added, and that the AI path
 * cannot be swapped out when the network is unavailable.
 *
 * <p><b>Why Strategy:</b> the algorithm varies while the calling code does not.
 * {@code TransactionService} depends only on this interface, so adding
 * {@code AICategorizationStrategy} in a later stage introduces one new class and
 * modifies no existing logic.
 *
 * <p><b>Alternative considered:</b> a single method with a mode flag. It keeps
 * all branches in one class, which grows without bound and forces every caller
 * to know which modes exist.
 *
 * <p>Implementations return {@link Optional#empty()} rather than guessing when
 * they cannot decide: an uncategorised transaction is a valid record, and a
 * wrong category is worse than none because it silently corrupts budget totals.
 */
public interface CategorizationStrategy {

    Optional<Category> categorize(Transaction transaction);

    /** Shown in the UI so the user can see which method produced a suggestion. */
    String name();
}
