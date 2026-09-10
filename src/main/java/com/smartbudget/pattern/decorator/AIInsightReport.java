package com.smartbudget.pattern.decorator;

import com.smartbudget.model.AIInsight;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.service.ReportService;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps any {@link Report} and appends a written commentary section.
 *
 * <p><b>Problem it solves:</b> the monthly report needed an interpretation layer
 * on top of the figures, and that layer is optional — the user can switch it
 * off, and it must not exist at all when there is no network. Putting the
 * narrative inside {@code BaseReport} would mean one class doing arithmetic and
 * prose, with a boolean flag threaded through it.
 *
 * <p><b>Why Decorator:</b> it adds behaviour to an object without changing the
 * object's class or its interface. {@code BaseReport} was not modified to make
 * this work, and callers cannot tell a wrapped report from a plain one. Because
 * the wrapper takes a {@code Report} rather than a {@code BaseReport}, another
 * decorator — a comparison against last month, say — could wrap this one in turn.
 *
 * <p><b>Alternative considered:</b> a subclass, {@code AIReport extends
 * BaseReport}. That fixes the combination at compile time: wanting the AI layer
 * over a quarterly report as well would need a second subclass, and every future
 * pairing another. Composition avoids that multiplication.
 */
public class AIInsightReport implements Report {

    private static final String SYSTEM_PROMPT = """
            You write a short monthly commentary on someone's personal spending.
            Use three or four sentences of plain language.
            Base every statement strictly on the figures given; invent nothing.
            Point out what changed, what went over budget, and what stayed under.
            Do not lecture, and do not give financial advice.""";

    private final Report delegate;
    private final AIProvider aiProvider;
    private final AIInsightDao insightDao;
    private final ReportService reportService;
    private final YearMonth month;

    public AIInsightReport(Report delegate, AIProvider aiProvider, AIInsightDao insightDao,
                           ReportService reportService, YearMonth month) {
        this.delegate = delegate;
        this.aiProvider = aiProvider;
        this.insightDao = insightDao;
        this.reportService = reportService;
        this.month = month;
    }

    @Override
    public String title() {
        return delegate.title();
    }

    @Override
    public List<ReportSection> sections() {
        // Everything the wrapped report produced, untouched, plus one more.
        List<ReportSection> sections = new ArrayList<>(delegate.sections());
        String narrative = narrative();
        sections.add(ReportSection.of(headingFor(narrative), narrative));
        store(narrative);
        return sections;
    }

    private String headingFor(String narrative) {
        return aiProvider.isAvailable() ? "Insight" : "Insight (generated offline)";
    }

    private String narrative() {
        return aiProvider.complete(SYSTEM_PROMPT, facts())
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElseGet(this::deterministicNarrative);
    }

    /** The figures handed to the model, so its commentary is grounded rather than invented. */
    private String facts() {
        StringBuilder facts = new StringBuilder();
        facts.append("Month: ").append(month).append('\n');
        facts.append(String.format("Income: %.2f%n", reportService.totalIncome(month)));
        facts.append(String.format("Spending: %.2f%n", reportService.totalSpent(month)));
        facts.append(String.format("Net: %.2f%n", reportService.netForMonth(month)));

        facts.append("Spending by category:\n");
        reportService.spendingByCategory(month).forEach((category, total) ->
                facts.append(String.format("  %s: %.2f%n", category, total)));

        facts.append("Budgets:\n");
        for (BudgetAlert status : reportService.budgetVsActual(month)) {
            facts.append(String.format("  %s: target %.2f, actual %.2f, %s%n",
                    status.categoryName(), status.target(), status.spent(), status.severity()));
        }
        return facts.toString();
    }

    /**
     * A commentary assembled from the same numbers, used when no model answers.
     *
     * <p>Not a placeholder: it states the largest category, the budgets that were
     * exceeded and whether the month ended ahead. The decorator therefore adds
     * real value with the network switched off, which is what makes it honest to
     * demonstrate offline.
     */
    private String deterministicNarrative() {
        StringBuilder text = new StringBuilder();

        double net = reportService.netForMonth(month);
        text.append(net >= 0
                ? String.format("You finished %s ahead by %.2f. ", month, net)
                : String.format("You spent %.2f more than you received in %s. ", -net, month));

        Map<String, Double> spending = reportService.spendingByCategory(month);
        spending.entrySet().stream().findFirst().ifPresent(largest ->
                text.append(String.format("The largest category was %s at %.2f. ",
                        largest.getKey(), largest.getValue())));

        List<String> exceeded = reportService.budgetVsActual(month).stream()
                .filter(status -> status.severity() == AlertSeverity.EXCEEDED)
                .map(BudgetAlert::categoryName)
                .toList();
        if (exceeded.isEmpty()) {
            text.append("No budget was exceeded this month.");
        } else {
            text.append("Over budget: ").append(String.join(", ", exceeded)).append('.');
        }
        return text.toString();
    }

    /**
     * Keeps the commentary alongside the transaction-level insights.
     *
     * <p>Identical text is not stored twice, so re-opening the Reports screen
     * does not fill the table with copies of the same paragraph.
     */
    private void store(String narrative) {
        boolean alreadyStored = insightDao.findByMonth(month).stream()
                .anyMatch(existing -> narrative.equals(existing.getGeneratedText()));
        if (!alreadyStored) {
            insightDao.insert(AIInsight.forReport(month, narrative));
        }
    }
}
