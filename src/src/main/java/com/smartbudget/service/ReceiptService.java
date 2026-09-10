package com.smartbudget.service;

import com.smartbudget.pattern.adapter.AIProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Extracts merchant, amount and date from a photographed receipt.
 *
 * <p>The result is always a <em>suggestion</em>. Vision models misread
 * handwritten totals and ambiguous dates, so nothing here saves a transaction —
 * the extraction is handed to the user to correct first. A receipt import that
 * silently writes a wrong amount is worse than no import at all.
 */
public class ReceiptService {

    /** Guard against loading a huge photo into memory and into a request body. */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private static final String PROMPT = """
            This image is a shop receipt.
            Reply with exactly one line in this format, and nothing else:
            merchant|amount|date
            The amount is the final total as a plain number, no currency symbol.
            The date is in YYYY-MM-DD format.
            If a field is not readable, write UNKNOWN in its place.""";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private final AIProvider aiProvider;

    public ReceiptService(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public boolean isAvailable() {
        return aiProvider.isAvailable();
    }

    /**
     * Reads an image file and asks the model to transcribe it.
     *
     * @throws ValidationException for problems the user can act on — a missing
     *         file, an oversized one, or AI not being configured
     */
    public Optional<ReceiptExtraction> extract(Path imagePath) {
        if (imagePath == null || !Files.isReadable(imagePath)) {
            throw new ValidationException("That image file could not be read.");
        }
        if (!aiProvider.isAvailable()) {
            throw new ValidationException(
                    "Receipt scanning needs an AI key. Add one to config.properties, "
                            + "or enter the transaction manually.");
        }

        byte[] bytes;
        try {
            long size = Files.size(imagePath);
            if (size > MAX_BYTES) {
                throw new ValidationException(
                        "That image is larger than 8 MB. Please use a smaller photo.");
            }
            bytes = Files.readAllBytes(imagePath);
        } catch (IOException e) {
            throw new ValidationException("That image file could not be read.");
        }

        return aiProvider.completeWithImage(PROMPT, bytes, mimeTypeOf(imagePath))
                .flatMap(answer -> parse(answer, imagePath));
    }

    /**
     * Parses the {@code merchant|amount|date} line.
     *
     * <p>Every field is optional in the result: a receipt where only the total is
     * legible is still useful, because the user fills in the rest. Refusing to
     * return anything unless all three parsed would throw away the useful part.
     */
    static Optional<ReceiptExtraction> parse(String answer, Path source) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        // Models sometimes wrap the line in prose or code fences despite the
        // instruction, so pick the first line that actually has the separator.
        String line = answer.lines()
                .map(String::trim)
                .map(text -> text.replaceAll("^[`*\\s]+|[`*\\s]+$", ""))
                .filter(text -> text.contains("|"))
                .findFirst()
                .orElse(null);
        if (line == null) {
            return Optional.empty();
        }

        String[] parts = line.split("\\|");
        String merchant = field(parts, 0);
        String amountText = field(parts, 1);
        String dateText = field(parts, 2);

        return Optional.of(new ReceiptExtraction(
                merchant,
                parseAmount(amountText).orElse(null),
                parseDate(dateText).orElse(null),
                source == null ? null : source.getFileName().toString(),
                line));
    }

    private static String field(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].trim();
        return value.isEmpty() || value.equalsIgnoreCase("UNKNOWN") ? null : value;
    }

    private static Optional<Double> parseAmount(String text) {
        if (text == null) {
            return Optional.empty();
        }
        // Strip currency words, symbols and thousands separators the model may
        // have left in despite being asked for a plain number.
        String cleaned = text.replaceAll("[^0-9.,]", "").replace(",", "");
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        try {
            double amount = Double.parseDouble(cleaned);
            return amount > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseDate(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String cleaned = text.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(cleaned, format));
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        return Optional.empty();
    }

    private static String mimeTypeOf(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    /**
     * What was read off a receipt. Any field may be null, meaning the model could
     * not read it and the user must supply it.
     */
    public record ReceiptExtraction(
            String merchant,
            Double amount,
            LocalDate date,
            String sourceFile,
            String rawAnswer) {

        public boolean isComplete() {
            return merchant != null && amount != null && date != null;
        }
    }
}
