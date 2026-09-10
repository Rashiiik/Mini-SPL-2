package com.smartbudget.pattern;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.model.Transaction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.adapter.NullAIProvider;
import com.smartbudget.pattern.command.AddTransactionCommand;
import com.smartbudget.pattern.command.Command;
import com.smartbudget.pattern.command.CommandHistory;
import com.smartbudget.pattern.command.NaturalLanguageParser;
import com.smartbudget.pattern.decorator.AIInsightReport;
import com.smartbudget.pattern.decorator.BaseReport;
import com.smartbudget.pattern.decorator.Report;
import com.smartbudget.pattern.decorator.ReportSection;
import com.smartbudget.pattern.observer.BudgetEventBus;
import com.smartbudget.pattern.strategy.RuleBasedCategorizationStrategy;
import com.smartbudget.persistence.Database;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.persistence.dao.AccountDao;
import com.smartbudget.persistence.dao.BudgetDao;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.persistence.dao.RecurringRuleDao;
import com.smartbudget.persistence.dao.TransactionDao;
import com.smartbudget.service.BudgetService;
import com.smartbudget.service.ReportService;
import com.smartbudget.service.TransactionService;
import com.smartbudget.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage7PatternsTest {

    private Database database;
    private TransactionDao transactionDao;
    private AccountDao accountDao;
    private CategoryDao categoryDao;
    private AIInsightDao insightDao;
    private ReportService reportService;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        transactionDao = new TransactionDao(database);
        accountDao = new AccountDao(database);
        categoryDao = new CategoryDao(database);
        insightDao = new AIInsightDao(database);
        BudgetDao budgetDao = new BudgetDao(database);
        RecurringRuleDao ruleDao = new RecurringRuleDao(database);

        BudgetService budgetService =
                new BudgetService(budgetDao, transactionDao, categoryDao, new BudgetEventBus());
        reportService = new ReportService(transactionDao, budgetDao, categoryDao, budgetService);
        transactionService = new TransactionService(transactionDao, accountDao,
                new RuleBasedCategorizationStrategy(categoryDao, ruleDao), budgetService);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private AIProvider providerReturning(String answer) {
        return new AIProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<String> complete(String systemPrompt, String userPrompt) {
                return Optional.ofNullable(answer);
            }

            @Override
            public Optional<String> completeWithImage(String p, byte[] b, String m) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "Stub";
            }
        };
    }

    @Nested
    @DisplayName("Decorator: AI insight layer on reports")
    class DecoratorTest {

        @Test
        @DisplayName("the base report carries the figures and no commentary")
        void baseReportSections() {
            List<ReportSection> sections = new BaseReport(reportService, YearMonth.now()).sections();

            assertEquals(3, sections.size());
            assertEquals("Summary", sections.get(0).heading());
            assertTrue(sections.stream().noneMatch(s -> s.heading().startsWith("Insight")));
        }

        @Test
        @DisplayName("the decorator keeps every section of what it wraps and adds one")
        void decoratorPreservesAndAdds() {
            YearMonth month = YearMonth.now();
            Report base = new BaseReport(reportService, month);
            Report decorated = new AIInsightReport(
                    base, new NullAIProvider(), insightDao, reportService, month);

            List<ReportSection> baseSections = base.sections();
            List<ReportSection> decoratedSections = decorated.sections();

            assertEquals(baseSections.size() + 1, decoratedSections.size());
            for (int i = 0; i < baseSections.size(); i++) {
                assertEquals(baseSections.get(i).heading(), decoratedSections.get(i).heading(),
                        "the wrapped report's own sections must be untouched");
            }
            assertTrue(decoratedSections.get(decoratedSections.size() - 1)
                    .heading().startsWith("Insight"));
        }

        @Test
        @DisplayName("the decorator does not change the wrapped report's title")
        void titleIsDelegated() {
            YearMonth month = YearMonth.now();
            Report base = new BaseReport(reportService, month);

            assertEquals(base.title(), new AIInsightReport(
                    base, new NullAIProvider(), insightDao, reportService, month).title());
        }

        @Test
        @DisplayName("without AI the commentary is still real, built from the same figures")
        void offlineNarrativeIsUseful() {
            YearMonth month = YearMonth.now();
            Report decorated = new AIInsightReport(new BaseReport(reportService, month),
                    new NullAIProvider(), insightDao, reportService, month);

            ReportSection insight = decorated.sections().get(decorated.sections().size() - 1);
            String text = insight.lines().get(0);

            assertTrue(insight.heading().contains("offline"));
            assertFalse(text.isBlank());
            assertTrue(text.contains(month.toString()) || text.contains("largest category")
                            || text.contains("budget"),
                    "offline commentary should reference the actual month or figures: " + text);
        }

        @Test
        @DisplayName("an AI commentary is used when the provider answers")
        void usesProviderNarrative() {
            YearMonth month = YearMonth.now();
            Report decorated = new AIInsightReport(new BaseReport(reportService, month),
                    providerReturning("Spending held steady this month."),
                    insightDao, reportService, month);

            ReportSection insight = decorated.sections().get(decorated.sections().size() - 1);

            assertEquals("Insight", insight.heading());
            assertEquals("Spending held steady this month.", insight.lines().get(0));
        }

        @Test
        @DisplayName("the commentary is stored once, not on every render")
        void narrativeStoredOnce() {
            YearMonth month = YearMonth.now();
            Report decorated = new AIInsightReport(new BaseReport(reportService, month),
                    providerReturning("Spending held steady this month."),
                    insightDao, reportService, month);

            decorated.sections();
            int afterFirst = insightDao.findByMonth(month).size();
            decorated.sections();

            assertEquals(afterFirst, insightDao.findByMonth(month).size());
        }

        @Test
        @DisplayName("a decorator can wrap a decorator, since it takes the interface")
        void decoratorsCompose() {
            YearMonth month = YearMonth.now();
            Report once = new AIInsightReport(new BaseReport(reportService, month),
                    new NullAIProvider(), insightDao, reportService, month);
            Report twice = new AIInsightReport(once,
                    new NullAIProvider(), insightDao, reportService, month);

            assertEquals(once.sections().size() + 1, twice.sections().size());
        }
    }

    @Nested
    @DisplayName("Command: natural-language entry with undo")
    class CommandTest {

        private Account account;
        private CommandHistory history;
        private NaturalLanguageParser parser;

        @BeforeEach
        void prepare() {
            account = accountDao.insert(
                    new Account(null, "Command Test", AccountType.CHECKING, 10_000, "BDT"));
            history = new CommandHistory();
            parser = new NaturalLanguageParser(new NullAIProvider());
        }

        @Test
        @DisplayName("a spending sentence becomes a negative transaction")
        void parsesSpending() {
            Transaction parsed = parser.parse("spent 450 on lunch", account.getId());

            assertEquals(-450, parsed.getAmount(), 0.001);
            assertEquals(LocalDate.now(), parsed.getDate());
            assertTrue(parsed.getDescription().toLowerCase().contains("lunch"));
        }

        @Test
        @DisplayName("an income sentence becomes a positive transaction")
        void parsesIncome() {
            assertEquals(65_000,
                    parser.parse("received 65000 salary", account.getId()).getAmount(), 0.001);
        }

        @Test
        @DisplayName("yesterday is understood")
        void parsesYesterday() {
            assertEquals(LocalDate.now().minusDays(1),
                    parser.parse("spent 200 on cng yesterday", account.getId()).getDate());
        }

        @Test
        @DisplayName("an explicit ISO date is understood")
        void parsesIsoDate() {
            LocalDate past = LocalDate.now().minusDays(5);
            assertEquals(past,
                    parser.parse("paid 900 for internet " + past, account.getId()).getDate());
        }

        @Test
        @DisplayName("a sentence with no amount is rejected with a usable message")
        void rejectsMissingAmount() {
            ValidationException error = assertThrows(ValidationException.class,
                    () -> parser.parse("bought some groceries", account.getId()));
            assertTrue(error.getMessage().contains("No amount"));
        }

        @Test
        @DisplayName("executing the command saves the transaction and moves the balance")
        void executeSaves() {
            Transaction parsed = parser.parse("spent 450 on lunch", account.getId());
            history.execute(new AddTransactionCommand(transactionService, parsed));

            assertEquals(9_550, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
            assertTrue(history.canUndo());
        }

        @Test
        @DisplayName("undo removes the transaction and restores the balance exactly")
        void undoRestores() {
            int before = transactionDao.findAll().size();
            Transaction parsed = parser.parse("spent 450 on lunch", account.getId());
            history.execute(new AddTransactionCommand(transactionService, parsed));

            history.undoLast();

            assertEquals(before, transactionDao.findAll().size());
            assertEquals(10_000, accountDao.findById(account.getId()).orElseThrow().getBalance(), 0.001);
            assertFalse(history.canUndo());
        }

        @Test
        @DisplayName("undo unwinds commands newest first")
        void undoIsLastInFirstOut() {
            history.execute(new AddTransactionCommand(transactionService,
                    parser.parse("spent 100 on tea", account.getId())));
            history.execute(new AddTransactionCommand(transactionService,
                    parser.parse("spent 900 on books", account.getId())));

            assertTrue(history.undoLast().orElseThrow().contains("900"));
            assertTrue(history.undoLast().orElseThrow().contains("100"));
            assertFalse(history.canUndo());
        }

        @Test
        @DisplayName("undoing with an empty history is harmless")
        void undoEmptyHistory() {
            assertTrue(history.undoLast().isEmpty());
        }

        @Test
        @DisplayName("a command that fails validation is not recorded as undoable")
        void failedCommandNotRecorded() {
            Transaction invalid = new Transaction(null, account.getId(), null, 0,
                    LocalDate.now(), "Zero amount", false);

            assertThrows(ValidationException.class,
                    () -> history.execute(new AddTransactionCommand(transactionService, invalid)));
            assertFalse(history.canUndo(), "a failed command must not be undoable");
        }

        @Test
        @DisplayName("a command reports whether it has run")
        void tracksExecutionState() {
            Command command = new AddTransactionCommand(transactionService,
                    parser.parse("spent 450 on lunch", account.getId()));

            assertFalse(command.isExecuted());
            command.execute();
            assertTrue(command.isExecuted());
            command.undo();
            assertFalse(command.isExecuted());
        }

        @Test
        @DisplayName("executing the same command twice is refused")
        void refusesDoubleExecute() {
            Command command = new AddTransactionCommand(transactionService,
                    parser.parse("spent 450 on lunch", account.getId()));
            command.execute();

            assertThrows(ValidationException.class, command::execute);
        }
    }
}
