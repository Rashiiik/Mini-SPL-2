package com.smartbudget.ui;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.pattern.observer.BudgetListener;
import com.smartbudget.service.AccountService;
import com.smartbudget.service.BudgetService;
import com.smartbudget.service.DashboardAdvisor;
import com.smartbudget.service.ReportService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Overview screen, and the Observer's subscriber.
 *
 * <p>It implements {@link BudgetListener} and registers itself on the event bus,
 * so a budget crossing anywhere in the application surfaces here. Nothing in
 * {@code BudgetService} knows this class exists — that is the point of the
 * pattern, and it is why a notification tray could be added later by writing one
 * new listener and changing no existing code.
 */
class DashboardView implements RefreshableView, BudgetListener {

    private final AccountService accountService;
    private final BudgetService budgetService;
    private final ReportService reportService;
    private final DashboardAdvisor advisor;

    private final VBox root = new VBox(14);
    private final FlowPane accountCards = new FlowPane(12, 12);
    private final VBox budgetRows = new VBox(8);
    private final VBox alertBanner = new VBox(4);
    private final Label summaryLabel = new Label();
    private final VBox overviewBox = new VBox(4);
    private final Label overviewText = new Label();
    private final Label overviewSource = new Label();

    /** One thread, so overlapping refreshes queue rather than race. */
    private final ExecutorService overviewExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dashboard-overview");
        thread.setDaemon(true);
        return thread;
    });

    /** Identifies the newest request, so a slow earlier answer cannot overwrite it. */
    private long overviewRequest;

    DashboardView(AccountService accountService, BudgetService budgetService,
                  ReportService reportService, DashboardAdvisor advisor) {
        this.accountService = accountService;
        this.budgetService = budgetService;
        this.reportService = reportService;
        this.advisor = advisor;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        alertBanner.setPadding(new Insets(10));
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);

        summaryLabel.setStyle("-fx-font-size: 14px;");

        Button dismiss = new Button("Dismiss alerts");
        dismiss.setOnAction(e -> clearAlerts());
        HBox alertHeader = new HBox(8, UiSupport.subtitle("Budget alerts"), dismiss);
        alertHeader.setAlignment(Pos.CENTER_LEFT);

        VBox alertSection = new VBox(6, alertHeader, alertBanner);

        overviewText.setWrapText(true);
        overviewText.setStyle("-fx-font-size: 13px;");
        overviewSource.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        overviewBox.setPadding(new Insets(12));
        overviewBox.setStyle("-fx-background-color: #eef4fb; -fx-border-color: #cfe0f1; "
                + "-fx-border-radius: 6; -fx-background-radius: 6;");
        overviewBox.getChildren().setAll(
                UiSupport.subtitle("AI overview"), overviewText, overviewSource);

        root.getChildren().addAll(
                UiSupport.title("Dashboard"),
                summaryLabel,
                overviewBox,
                UiSupport.subtitle("Accounts"),
                accountCards,
                UiSupport.subtitle("This month's budgets"),
                budgetRows,
                alertSection);
        VBox.setVgrow(budgetRows, Priority.SOMETIMES);
    }

    /**
     * Called from the event bus when spending crosses a threshold.
     *
     * <p>Wrapped in {@link Platform#runLater} because a listener may in future be
     * notified from a background thread (the AI stages do network work), and
     * JavaFX controls may only be touched on the FX application thread.
     */
    @Override
    public void onBudgetAlert(BudgetAlert alert) {
        Platform.runLater(() -> {
            Label label = new Label("• " + alert.message());
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: " + colourFor(alert.severity()) + "; -fx-font-weight: bold;");
            alertBanner.getChildren().add(label);
            alertBanner.setStyle("-fx-background-color: #fdf3e7; -fx-border-color: "
                    + colourFor(alert.severity()) + "; -fx-border-radius: 4; -fx-background-radius: 4;");
            alertBanner.setVisible(true);
            alertBanner.setManaged(true);
        });
    }

    private void clearAlerts() {
        alertBanner.getChildren().clear();
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }

    private static String colourFor(AlertSeverity severity) {
        return switch (severity) {
            case EXCEEDED -> "#c0392b";
            case WARNING -> "#e67e22";
            case OK -> "#27ae60";
        };
    }

    private Node accountCard(Account account) {
        boolean credit = account.getType() == AccountType.CREDIT;

        Label name = new Label(account.getName());
        name.setStyle("-fx-font-weight: bold;");
        Label type = new Label(credit ? "Credit card — owed" : account.getType() + " — available");
        type.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        Label balance = new Label(account.getCurrency() + " " + UiSupport.money(account.getBalance()));
        balance.setStyle("-fx-font-size: 18px; -fx-text-fill: "
                + (credit ? "#c0392b" : "#2c3e50") + ";");

        VBox card = new VBox(4, name, type, balance);
        card.setPadding(new Insets(12));
        card.setPrefWidth(220);
        card.setStyle("-fx-background-color: #f7f9fa; -fx-border-color: #dfe4e6; "
                + "-fx-border-radius: 6; -fx-background-radius: 6;");
        return card;
    }

    private Node budgetRow(BudgetAlert status) {
        Label name = new Label(status.categoryName());
        name.setPrefWidth(160);

        ProgressBar bar = new ProgressBar(Math.min(status.utilisation(), 1.0));
        bar.setPrefWidth(240);
        bar.setStyle("-fx-accent: " + colourFor(status.severity()) + ";");

        Label figures = new Label(UiSupport.money(status.spent()) + " of "
                + UiSupport.money(status.target()) + "  (" + UiSupport.percent(status.utilisation()) + ")");
        figures.setStyle("-fx-text-fill: " + colourFor(status.severity()) + ";");

        HBox row = new HBox(12, name, bar, figures);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        YearMonth month = YearMonth.now();

        accountCards.getChildren().setAll(
                accountService.findAll().stream().map(this::accountCard).toList());

        List<BudgetAlert> statuses = budgetService.statusFor(month);
        if (statuses.isEmpty()) {
            Label empty = new Label("No budgets set for " + month + ". Add one on the Budgets tab.");
            empty.setStyle("-fx-text-fill: #777777;");
            budgetRows.getChildren().setAll(empty);
        } else {
            budgetRows.getChildren().setAll(statuses.stream().map(this::budgetRow).toList());
        }

        summaryLabel.setText(String.format(
                "%s   ·   Net worth %s   ·   Income %s   ·   Spending %s   ·   Net %s",
                month,
                UiSupport.money(accountService.netWorth()),
                UiSupport.money(reportService.totalIncome(month)),
                UiSupport.money(reportService.totalSpent(month)),
                UiSupport.signedMoney(reportService.netForMonth(month))));

        refreshOverview(month);
    }

    /**
     * Reads the month's figures here, then writes the briefing on a background
     * thread, because that step may call out to a model over the network and the
     * dashboard must not freeze while it does.
     */
    private void refreshOverview(YearMonth month) {
        DashboardAdvisor.Briefing briefing = advisor.gather(month);

        if (overviewText.getText().isEmpty()) {
            overviewText.setText("Reading this month's figures…");
        }

        long request = ++overviewRequest;
        overviewExecutor.execute(() -> {
            DashboardAdvisor.Overview overview = advisor.explain(briefing);
            Platform.runLater(() -> {
                if (request == overviewRequest) {
                    overviewText.setText(overview.text());
                    overviewSource.setText(overview.fromModel()
                            ? "Written by " + advisor.providerName() + " from the figures on this page."
                            : "Generated directly from the figures on this page.");
                }
            });
        });
    }

    /** Stops the background writer when the application closes. */
    void dispose() {
        overviewExecutor.shutdownNow();
    }
}
