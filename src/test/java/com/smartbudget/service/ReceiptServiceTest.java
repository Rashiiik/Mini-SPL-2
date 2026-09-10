package com.smartbudget.service;

import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.service.ReceiptService.ReceiptExtraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing is tested directly rather than through the network, because what
 * matters is how the application copes with everything a vision model might
 * return — including partial and badly formatted answers.
 */
class ReceiptServiceTest {

    @Test
    @DisplayName("a well-formed answer is parsed into all three fields")
    void parsesCleanAnswer() {
        ReceiptExtraction extraction =
                ReceiptService.parse("SHWAPNO SUPERSTORE|1450.00|2026-09-05", null).orElseThrow();

        assertEquals("SHWAPNO SUPERSTORE", extraction.merchant());
        assertEquals(1450.00, extraction.amount(), 0.001);
        assertEquals(LocalDate.of(2026, 9, 5), extraction.date());
        assertTrue(extraction.isComplete());
    }

    @Test
    @DisplayName("surrounding prose and code fences are ignored")
    void ignoresSurroundingText() {
        String answer = """
                Sure, here is the information:
                ```
                Meena Bazar|860.50|2026-08-14
                ```
                Let me know if you need anything else.""";

        ReceiptExtraction extraction = ReceiptService.parse(answer, null).orElseThrow();

        assertEquals("Meena Bazar", extraction.merchant());
        assertEquals(860.50, extraction.amount(), 0.001);
    }

    @Test
    @DisplayName("a currency symbol left in the amount is stripped")
    void stripsCurrency() {
        assertEquals(1450.00,
                ReceiptService.parse("Shop|BDT 1,450.00|2026-09-05", null)
                        .orElseThrow().amount(), 0.001);
    }

    @Test
    @DisplayName("alternative date formats are accepted")
    void acceptsOtherDateFormats() {
        assertEquals(LocalDate.of(2026, 9, 5),
                ReceiptService.parse("Shop|100|05-09-2026", null).orElseThrow().date());
        assertEquals(LocalDate.of(2026, 9, 5),
                ReceiptService.parse("Shop|100|05/09/2026", null).orElseThrow().date());
    }

    @Test
    @DisplayName("UNKNOWN fields come back as null rather than sinking the whole read")
    void partialReadIsStillUseful() {
        ReceiptExtraction extraction =
                ReceiptService.parse("UNKNOWN|1450.00|UNKNOWN", null).orElseThrow();

        assertNull(extraction.merchant());
        assertEquals(1450.00, extraction.amount(), 0.001);
        assertNull(extraction.date());
        assertFalse(extraction.isComplete(), "the form should ask the user to fill the gaps");
    }

    @Test
    @DisplayName("an unparseable date leaves the date blank but keeps the amount")
    void badDateKeepsAmount() {
        ReceiptExtraction extraction =
                ReceiptService.parse("Shop|500|last Tuesday", null).orElseThrow();

        assertEquals(500, extraction.amount(), 0.001);
        assertNull(extraction.date());
    }

    @Test
    @DisplayName("a negative or zero total is treated as unreadable")
    void rejectsNonPositiveAmount() {
        assertNull(ReceiptService.parse("Shop|0|2026-09-05", null).orElseThrow().amount());
        assertNull(ReceiptService.parse("Shop|abc|2026-09-05", null).orElseThrow().amount());
    }

    @Test
    @DisplayName("an answer with no separator yields nothing")
    void rejectsUnusableAnswer() {
        assertEquals(Optional.empty(),
                ReceiptService.parse("I cannot read this image.", null));
        assertEquals(Optional.empty(), ReceiptService.parse("", null));
        assertEquals(Optional.empty(), ReceiptService.parse(null, null));
    }

    @Test
    @DisplayName("scanning without an AI key explains what to do instead of failing silently")
    void requiresApiKey(@TempDir Path tempDir) throws IOException {
        Path image = tempDir.resolve("receipt.png");
        Files.write(image, new byte[]{1, 2, 3});

        ValidationException error = assertThrows(ValidationException.class,
                () -> new ReceiptService(new NullAIProvider()).extract(image));

        assertTrue(error.getMessage().contains("AI key"));
    }

    @Test
    @DisplayName("a missing file is reported to the user, not thrown as an IO error")
    void missingFileIsReported() {
        assertThrows(ValidationException.class,
                () -> new ReceiptService(new NullAIProvider()).extract(Path.of("no-such-file.png")));
    }
}
