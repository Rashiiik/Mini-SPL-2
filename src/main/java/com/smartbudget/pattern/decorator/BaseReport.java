package com.smartbudget.pattern.decorator;

import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.service.ReportService;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The plain numeric monthly report: totals, spending breakdown, and budget
 * performance. Contains no AI and needs no network.
 */
public class BaseReport implements Report {

    private final ReportService reportService;
    private final YearMonth month;

    public BaseReport(ReportService reportService, YearMonth month) {
        this.reportService = reportService;
        this.month = month;
    }

    public YearMonth month() {
        return month;
    }

    @Override
    public String title() {
        return "Monthly report — " + month;
    }

    @Override
    public List<ReportSection> sections() {
        List<ReportSection> sections = new ArrayList<>();

        double income = reportService.totalIncome(month);
        double spent = reportService.totalSpent(month);
        double net = reportService.netForMonth(month);
        sections.add(ReportSection.of("Summary",
                String.format("Income: %.2f", income),
                String.format("Spending: %.2f", spent),
                String.format("Net: %s%.2f", net < 0 ? "-" : "+", Math.abs(net))));

        List<String> breakdown = new ArrayList<>();
        Map<String, Double> spending = reportService.spendingByCategory(month);
        if (spending.isEmpty()) {
            breakdown.add("No spending recorded this month.");
        } else {
            spending.forEach((category, total) -> breakdown.add(
                    String.format("%-18s %10.2f   %5.1f%%", category, total,
                            spent > 0 ? total / spent * 100 : 0)));
        }
        sections.add(new ReportSection("Spending by category", breakdown));

        List<String> budgets = new ArrayList<>();
        List<BudgetAlert> comparison = reportService.budgetVsActual(month);
        if (comparison.isEmpty()) {
            budgets.add("No budgets were set for this month.");
        } else {
            for (BudgetAlert status : comparison) {
                budgets.add(String.format("%-18s target %9.2f   actual %9.2f   %s",
                        status.categoryName(), status.target(), status.spent(),
                        status.severity()));
            }
        }
        sections.add(new ReportSection("Budget vs actual", budgets));

        return sections;
    }
}
