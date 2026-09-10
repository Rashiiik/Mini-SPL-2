package com.smartbudget.pattern.command;

import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.service.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a typed sentence such as "spent 450 on lunch yesterday" into a
 * {@link Transaction} ready to be wrapped in a command.
 *
 * <p>Tries the model first and falls back to a regex parser, the same shape as
 * categorisation. The fallback is not a stub: it handles the common phrasings on
 * its own, so free-text entry keeps working with no key configured.
 */
public class NaturalLanguageParser {

    private static final String SYSTEM_PROMPT = """
            You convert a sentence about a personal transaction into three fields.
            Reply with exactly one line and nothing else:
            amount|description|date
            The amount is negative for money spent and positive for money received.
            The description is a short merchant or purpose, two or three words.
            The date is YYYY-MM-DD. If no date is mentioned, use the given today's date.""";

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** Words implying money coming in; anything else is treated as spending. */
    private static final List<String> INCOME_WORDS =
            List.of("received", "got", "earned", "income", "salary", "refund", "refunded", "credited");

    private static final List<String> NOISE_WORDS =
            List.of("spent", "paid", "pay", "bought", "buy", "for", "on", "at", "taka", "bdt",
                    "tk", "received", "got", "earned", "today", "yesterday", "the", "a", "an");

    private final AIProvider aiProvider;

    public NaturalLanguageParser(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public Transaction parse(String text, int accountId) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Type something like: spent 450 on lunch yesterday");
        }

        Transaction parsed = aiProvider.isAvailable()
                ? aiProvider.complete(SYSTEM_PROMPT, text + "\nToday's date: " + LocalDate.now())
                        .flatMap(answer -> fromModelAnswer(answer, accountId))
                        .orElseGet(() -> fallbackParse(text, accountId))
                : fallbackParse(text, accountId);

        if (parsed.getAmount() == 0) {
            throw new ValidationException(
                    "No amount found in \"" + text + "\". Try: spent 450 on lunch yesterday");
        }
        return parsed;
    }

    /** Reads the {@code amount|description|date} line, rejecting anything unusable. */
    private Optional<Transaction> fromModelAnswer(String answer, int accountId) {
        String line = answer.lines()
                .map(String::trim)
                .filter(candidate -> candidate.contains("|"))
                .findFirst()
                .orElse(null);
        if (line == null) {
            return Optional.empty();
        }

        String[] parts = line.split("\\|");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            double amount = Double.parseDouble(parts[0].replaceAll("[^0-9.\\-]", ""));
            if (amount == 0) {
                return Optional.empty();
            }
            String description = parts[1].trim();
            if (description.isEmpty()) {
                return Optional.empty();
            }
            LocalDate date = parts.length >= 3 ? parseDate(parts[2].trim()) : LocalDate.now();

            // The model occasionally dates things in the future; the service
            // would reject that, so clamp it here rather than fail the whole entry.
            if (date.isAfter(LocalDate.now())) {
                date = LocalDate.now();
            }
            return Optional.of(new Transaction(
                    null, accountId, null, amount, date, description, false));
        } catch (NumberFormatException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private LocalDate parseDate(String text) {
        Matcher matcher = ISO_DATE.matcher(text);
        return matcher.find() ? LocalDate.parse(matcher.group(1)) : LocalDate.now();
    }

    /**
     * The offline parser: first number is the amount, leading verb decides the
     * sign, a date word sets the date, and what remains becomes the description.
     */
    Transaction fallbackParse(String text, int accountId) {
        String lowered = text.toLowerCase().trim();

        Matcher amountMatcher = AMOUNT.matcher(lowered);
        double magnitude = 0;
        String amountToken = null;
        if (amountMatcher.find()) {
            amountToken = amountMatcher.group(1);
            magnitude = Double.parseDouble(amountToken.replace(",", "."));
        }

        boolean isIncome = INCOME_WORDS.stream().anyMatch(lowered::contains);
        double amount = isIncome ? magnitude : -magnitude;

        LocalDate date = LocalDate.now();
        Matcher isoMatcher = ISO_DATE.matcher(lowered);
        if (isoMatcher.find()) {
            date = LocalDate.parse(isoMatcher.group(1));
        } else if (lowered.contains("yesterday")) {
            date = LocalDate.now().minusDays(1);
        }

        String description = buildDescription(lowered, amountToken);
        return new Transaction(null, accountId, null, amount, date, description, false);
    }

    /** What is left once the amount, the date words and the filler are removed. */
    private String buildDescription(String lowered, String amountToken) {
        String remaining = lowered;
        if (amountToken != null) {
            remaining = remaining.replaceFirst(Pattern.quote(amountToken), " ");
        }
        remaining = remaining.replaceAll(ISO_DATE.pattern(), " ");

        StringBuilder description = new StringBuilder();
        for (String word : remaining.split("\\s+")) {
            String cleaned = word.replaceAll("[^a-z]", "");
            if (cleaned.isEmpty() || NOISE_WORDS.contains(cleaned)) {
                continue;
            }
            if (description.length() > 0) {
                description.append(' ');
            }
            description.append(cleaned);
        }

        if (description.length() == 0) {
            return "Manual entry";
        }
        // Capitalise, so entries look like the rest of the table.
        return Character.toUpperCase(description.charAt(0)) + description.substring(1);
    }
}
