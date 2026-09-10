package com.smartbudget.service;

import com.smartbudget.model.UserAction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Writes the dashboard's briefing: which alerts matter, and what to do next.
 *
 * <p>Deliberately split in two. {@link #gather} reads the database, and the
 * application shares a single SQLite connection, so it must run on the same
 * thread as the rest of the UI's reads. {@link #explain} makes the network call
 * and must therefore not run on the JavaFX thread. Passing a {@link Briefing}
 * between the two keeps the connection on one thread without a lock.
 */
public class DashboardAdvisor {

    private static final String SYSTEM_PROMPT = """
            You brief someone on their own budget in three or four short sentences.
            First name the alerts that need attention, worst first.
            Then give one or two concrete next steps that follow from the figures.
            Base every statement strictly on the figures given; invent nothing.
            Plain language. No greeting, no lecturing, no investment advice.""";

    /** The figures for the model, plus the briefing to use when no model answers. */
    public record Briefing(String facts, String fallback) {
    }

    /** The finished briefing, and whether a model or the arithmetic produced it. */
    public record Overview(String text, boolean fromModel) {
    }

    private final AIProvider aiProvider;
    private final ReportService reportService;
    private final AnomalyService anomalyService;

    private String lastFacts;
    private Overview lastOverview;

    public DashboardAdvisor(AIProvider aiProvider, ReportService reportService,
                            AnomalyService anomalyService) {
        this.aiProvider = aiProvider;
        this.reportService = reportService;
        this.anomalyService = anomalyService;
    }

    public String providerName() {
        return aiProvider.name();
    }

    /** Reads the month's figures. Runs on the thread that owns the database. */
    public Briefing gather(YearMonth month) {
        List<BudgetAlert> statuses = reportService.budgetVsActual(month);
        long awaitingReview = anomalyService.storedAnomalies().stream()
                .filter(insight -> insight.getUserAction() == UserAction.PENDING)
                .count();
        return new Briefing(facts(month, statuses, awaitingReview),
                fallback(month, statuses, awaitingReview));
    }

    /**
     * Turns figures into prose. Performs a network call, so callers must run it
     * off the JavaFX thread.
     *
     * <p>The last answer is reused when the figures have not moved. The dashboard
     * refreshes after every save and on every tab switch, and without this each
     * of those would be another request for a briefing that could not have changed.
     */
    public Overview explain(Briefing briefing) {
        if (briefing.facts().equals(lastFacts)) {
            return lastOverview;
        }
        Overview overview = aiProvider.complete(SYSTEM_PROMPT, briefing.facts())
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> new Overview(text, true))
                .orElseGet(() -> new Overview(briefing.fallback(), false));

        lastFacts = briefing.facts();
        lastOverview = overview;
        return overview;
    }

    private String facts(YearMonth month, List<BudgetAlert> statuses, long awaitingReview) {
        StringBuilder facts = new StringBuilder();
        facts.append("Month: ").append(month).append('\n');
        facts.append(String.format("Income: %.2f%n", reportService.totalIncome(month)));
        facts.append(String.format("Spending: %.2f%n", reportService.totalSpent(month)));
        facts.append(String.format("Net: %.2f%n", reportService.netForMonth(month)));
        facts.append(String.format("Days left in month: %d%n", daysLeft(month)));

        facts.append("Budgets:\n");
        for (BudgetAlert status : statuses) {
            facts.append(String.format("  %s: target %.2f, actual %.2f, %s%n",
                    status.categoryName(), status.target(), status.spent(), status.severity()));
        }

        facts.append("Spending by category:\n");
        reportService.spendingByCategory(month).forEach((category, total) ->
                facts.append(String.format("  %s: %.2f%n", category, total)));

        facts.append(String.format("Flagged transactions awaiting review: %d%n", awaitingReview));
        return facts.toString();
    }

    /**
     * The same briefing assembled from the figures alone.
     *
     * <p>Not a placeholder for the model's answer: it names the worst breach, the
     * category with the most headroom to cover it from, and the reviews still
     * outstanding. The dashboard is therefore useful with the network switched off.
     */
    private String fallback(YearMonth month, List<BudgetAlert> statuses, long awaitingReview) {
        List<BudgetAlert> exceeded = bySeverity(statuses, AlertSeverity.EXCEEDED);
        List<BudgetAlert> warning = bySeverity(statuses, AlertSeverity.WARNING);
        long daysLeft = daysLeft(month);

        StringBuilder text = new StringBuilder();

        if (!exceeded.isEmpty()) {
            BudgetAlert worst = exceeded.get(0);
            text.append(String.format("%s is over its %.2f budget by %.2f — the alert to deal with first.",
                    worst.categoryName(), worst.target(), worst.overspend()));
            if (exceeded.size() > 1) {
                text.append(String.format(" %s also went over.", names(exceeded.subList(1, exceeded.size()))));
            }
            text.append(daysLeft > 0
                    ? String.format(" Next step: stop charging %s for the %d days left in %s.",
                            worst.categoryName(), daysLeft, month)
                    : String.format(" Next step: raise the %s budget to a figure you can hold, or plan the month around the lower one.",
                            worst.categoryName()));
            headroom(statuses).ifPresent(spare -> text.append(String.format(
                    " %s is running %.2f under its budget, so that is where the room is.",
                    spare.categoryName(), spare.target() - spare.spent())));
        } else if (!warning.isEmpty()) {
            BudgetAlert closest = warning.get(0);
            text.append(String.format("Nothing is over budget yet, but %s is at %.0f%% of its %.2f limit.",
                    closest.categoryName(), closest.utilisation() * 100, closest.target()));
            text.append(daysLeft > 0
                    ? String.format(" Next step: keep %s under %.2f for the %d days left in %s.",
                            closest.categoryName(), closest.target() - closest.spent(), daysLeft, month)
                    : String.format(" Next step: check whether the %s budget is set at the right level.",
                            closest.categoryName()));
        } else if (statuses.isEmpty()) {
            text.append("No budgets are set for ").append(month)
                    .append(", so there is nothing to measure this month's spending against.")
                    .append(" Next step: set a budget for your largest category on the Budgets tab.");
        } else {
            text.append("Every budget is within its limit this month, so there is no alert to act on.");
        }

        if (awaitingReview > 0) {
            text.append(String.format(
                    " %d flagged transaction%s still waiting on the Insights tab; accepting or dismissing them keeps later alerts accurate.",
                    awaitingReview, awaitingReview == 1 ? " is" : "s are"));
        }

        double net = reportService.netForMonth(month);
        text.append(net >= 0
                ? String.format(" Overall you are %.2f ahead for %s.", net, month)
                : String.format(" Overall you are %.2f behind for %s.", -net, month));

        return text.toString();
    }

    /** Breaches worst first, so the briefing leads with the one that matters most. */
    private static List<BudgetAlert> bySeverity(List<BudgetAlert> statuses, AlertSeverity severity) {
        return statuses.stream()
                .filter(status -> status.severity() == severity)
                .sorted(Comparator.comparingDouble(BudgetAlert::utilisation).reversed())
                .toList();
    }

    /** The budget with the most left in it, used to suggest where an overspend can come from. */
    private static Optional<BudgetAlert> headroom(List<BudgetAlert> statuses) {
        return statuses.stream()
                .filter(status -> status.severity() == AlertSeverity.OK)
                .filter(status -> status.target() - status.spent() > 0)
                .max(Comparator.comparingDouble(status -> status.target() - status.spent()));
    }

    private static String names(List<BudgetAlert> statuses) {
        return String.join(", ", statuses.stream().map(BudgetAlert::categoryName).toList());
    }

    private static long daysLeft(YearMonth month) {
        LocalDate today = LocalDate.now();
        return YearMonth.from(today).equals(month)
                ? ChronoUnit.DAYS.between(today, month.atEndOfMonth())
                : 0;
    }
}
