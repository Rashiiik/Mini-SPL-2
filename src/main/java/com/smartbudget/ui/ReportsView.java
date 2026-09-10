package com.smartbudget.ui;

import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.pattern.decorator.AIInsightReport;
import com.smartbudget.pattern.decorator.BaseReport;
import com.smartbudget.pattern.decorator.Report;
import com.smartbudget.pattern.decorator.ReportSection;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.persistence.dao.AIInsightDao;
import com.smartbudget.service.ReportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * The two analytical operations: spending by category, and budget versus actual.
 *
 * <p>Reads only from {@code ReportService}, which is what lets Stage 7 wrap that
 * service in the AI-insight Decorator without this screen changing.
 */
class ReportsView implements RefreshableView {

    private final ReportService reportService;
    private final AIProvider aiProvider;
    private final AIInsightDao insightDao;

    private final VBox root = new VBox(12);
    private final ComboBox<YearMonth> monthBox = new ComboBox<>();
    private final PieChart breakdownChart = new PieChart();
    private final TableView<BudgetAlert> comparisonTable = new TableView<>();
    private final Label summaryLabel = new Label();
    private final CheckBox insightToggle = new CheckBox("Include AI insight layer");
    private final VBox reportBox = new VBox(6);

    ReportsView(ReportService reportService, AIProvider aiProvider, AIInsightDao insightDao) {
        this.reportService = reportService;
        this.aiProvider = aiProvider;
        this.insightDao = insightDao;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        monthBox.setOnAction(e -> UiSupport.guard(this::refreshContent));

        breakdownChart.setTitle("Spending by category");
        breakdownChart.setPrefHeight(320);
        // Slice labels are positioned by PieChart during layout, and land on top
        // of one another when the data is set while this tab is not on screen.
        // The legend carries the same "Category (amount)" text and lays out reliably.
        breakdownChart.setLabelsVisible(false);
        breakdownChart.setLegendVisible(true);

        TableColumn<BudgetAlert, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoryName()));
        categoryColumn.setPrefWidth(180);

        TableColumn<BudgetAlert, String> targetColumn = new TableColumn<>("Budgeted");
        targetColumn.setCellValueFactory(c ->
                new SimpleStringProperty(UiSupport.money(c.getValue().target())));
        targetColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, String> actualColumn = new TableColumn<>("Actual");
        actualColumn.setCellValueFactory(c ->
                new SimpleStringProperty(UiSupport.money(c.getValue().spent())));
        actualColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, String> varianceColumn = new TableColumn<>("Variance");
        varianceColumn.setCellValueFactory(c -> new SimpleStringProperty(
                UiSupport.signedMoney(c.getValue().target() - c.getValue().spent())));
        varianceColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().severity().name()));
        statusColumn.setPrefWidth(110);

        comparisonTable.getColumns().addAll(categoryColumn, targetColumn, actualColumn,
                varianceColumn, statusColumn);
        VBox.setVgrow(comparisonTable, Priority.ALWAYS);

        summaryLabel.setStyle("-fx-font-size: 14px;");

        Button generateButton = new Button("Generate report");
        generateButton.setOnAction(e -> generateReport());

        // Toggling this swaps a decorated report for a plain one at runtime,
        // which is the Decorator pattern visible in a single click.
        insightToggle.setSelected(false);

        HBox header = new HBox(8, new Label("Month"), monthBox, generateButton, insightToggle);
        header.setAlignment(Pos.CENTER_LEFT);

        reportBox.setPadding(new Insets(12));
        reportBox.setStyle("-fx-background-color: #f7f9fa; -fx-border-color: #dfe4e6; "
                + "-fx-border-radius: 4; -fx-background-radius: 4;");
        reportBox.setVisible(false);
        reportBox.setManaged(false);

        root.getChildren().addAll(
                UiSupport.title("Reports"),
                header, summaryLabel, breakdownChart,
                reportBox,
                UiSupport.subtitle("Budget versus actual"),
                comparisonTable);
    }

    /**
     * Builds a plain report, optionally wrapped in the AI-insight decorator.
     *
     * <p>The wrapped and unwrapped objects are used through the same
     * {@link Report} interface, so the rendering below cannot tell them apart.
     */
    private void generateReport() {
        UiSupport.guard(() -> {
            YearMonth month = monthBox.getValue() == null ? YearMonth.now() : monthBox.getValue();

            Report report = new BaseReport(reportService, month);
            if (insightToggle.isSelected()) {
                report = new AIInsightReport(report, aiProvider, insightDao, reportService, month);
            }

            reportBox.getChildren().clear();
            Label heading = new Label(report.title());
            heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
            reportBox.getChildren().add(heading);

            for (ReportSection section : report.sections()) {
                Label sectionHeading = new Label(section.heading());
                sectionHeading.setStyle("-fx-font-weight: bold;");
                reportBox.getChildren().add(sectionHeading);
                for (String line : section.lines()) {
                    Label lineLabel = new Label(line);
                    lineLabel.setWrapText(true);
                    lineLabel.setStyle("-fx-font-family: 'Consolas', monospace;");
                    reportBox.getChildren().add(lineLabel);
                }
            }
            reportBox.setVisible(true);
            reportBox.setManaged(true);
        });
    }

    private void refreshContent() {
        YearMonth month = monthBox.getValue() == null ? YearMonth.now() : monthBox.getValue();

        Map<String, Double> breakdown = reportService.spendingByCategory(month);
        breakdownChart.setData(FXCollections.observableArrayList(
                breakdown.entrySet().stream()
                        .map(entry -> new PieChart.Data(
                                entry.getKey() + " (" + UiSupport.money(entry.getValue()) + ")",
                                entry.getValue()))
                        .toList()));

        comparisonTable.setItems(
                FXCollections.observableArrayList(reportService.budgetVsActual(month)));

        double income = reportService.totalIncome(month);
        double spent = reportService.totalSpent(month);
        double net = reportService.netForMonth(month);
        summaryLabel.setText(String.format(
                "Income %s     Spending %s     Net %s",
                UiSupport.money(income), UiSupport.money(spent), UiSupport.signedMoney(net)));
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        YearMonth current = YearMonth.now();
        YearMonth selected = monthBox.getValue();
        List<YearMonth> months = List.of(
                current.minusMonths(3), current.minusMonths(2), current.minusMonths(1), current);
        monthBox.setItems(FXCollections.observableArrayList(months));
        monthBox.getSelectionModel().select(selected == null ? current : selected);
        refreshContent();
    }
}
