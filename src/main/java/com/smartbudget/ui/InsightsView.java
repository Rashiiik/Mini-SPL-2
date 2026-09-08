package com.smartbudget.ui;

import com.smartbudget.model.AIInsight;
import com.smartbudget.model.Transaction;
import com.smartbudget.model.UserAction;
import com.smartbudget.pattern.adapter.AIProvider;
import com.smartbudget.service.AnomalyService;
import com.smartbudget.service.TransactionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Unusual transactions, with the reason each was flagged.
 *
 * <p>Every insight records whether the user accepted or rejected it, which is
 * what keeps the AI advisory: nothing here changes a figure in the application,
 * it only draws attention to one.
 */
class InsightsView implements RefreshableView {

    private final AnomalyService anomalyService;
    private final TransactionService transactionService;
    private final AIProvider aiProvider;

    private final VBox root = new VBox(12);
    private final TableView<AIInsight> table = new TableView<>();
    private final ComboBox<YearMonth> monthBox = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final Label detailLabel = new Label();
    private final VBox detailBox = new VBox(4);

    InsightsView(AnomalyService anomalyService, TransactionService transactionService,
                 AIProvider aiProvider) {
        this.anomalyService = anomalyService;
        this.transactionService = transactionService;
        this.aiProvider = aiProvider;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        TableColumn<AIInsight, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(c -> new SimpleStringProperty(
                transactionFor(c.getValue()).map(t -> t.getDate().toString()).orElse("—")));
        dateColumn.setPrefWidth(100);

        TableColumn<AIInsight, String> descriptionColumn = new TableColumn<>("Transaction");
        descriptionColumn.setCellValueFactory(c -> new SimpleStringProperty(
                transactionFor(c.getValue()).map(Transaction::getDescription).orElse("—")));
        descriptionColumn.setPrefWidth(200);

        TableColumn<AIInsight, String> amountColumn = new TableColumn<>("Amount");
        amountColumn.setCellValueFactory(c -> new SimpleStringProperty(
                transactionFor(c.getValue())
                        .map(t -> UiSupport.money(t.getAbsoluteAmount()))
                        .orElse("—")));
        amountColumn.setPrefWidth(110);

        TableColumn<AIInsight, String> explanationColumn = new TableColumn<>("Why it was flagged");
        explanationColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getGeneratedText()));
        explanationColumn.setCellFactory(UiSupport.wrappingCell());
        explanationColumn.setMinWidth(260);
        explanationColumn.setPrefWidth(420);

        TableColumn<AIInsight, String> actionColumn = new TableColumn<>("Your decision");
        actionColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUserAction().name().toLowerCase()));
        actionColumn.setPrefWidth(120);

        table.getColumns().addAll(dateColumn, descriptionColumn, amountColumn,
                explanationColumn, actionColumn);
        table.setPlaceholder(new Label("No unusual transactions found yet. Run a scan to check."));
        // Give leftover width to the explanation column rather than to empty space.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showDetail(selected));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button scanButton = new Button("Scan month for anomalies");
        scanButton.setOnAction(e -> scan());

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> record(UserAction.ACCEPTED));

        Button rejectButton = new Button("Not unusual");
        rejectButton.setOnAction(e -> record(UserAction.REJECTED));

        HBox actions = new HBox(8, new Label("Month"), monthBox, scanButton,
                new Label("   Selected:"), acceptButton, rejectButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setStyle("-fx-text-fill: #555555;");

        // A guaranteed-readable copy of the selected explanation. Wrapping cells
        // handle most cases, but a very long sentence in a narrow window would
        // still be awkward to read inside a table row.
        detailLabel.setWrapText(true);
        detailLabel.setStyle("-fx-font-size: 13px;");
        detailBox.setPadding(new Insets(10));
        detailBox.setStyle("-fx-background-color: #f7f9fa; -fx-border-color: #dfe4e6; "
                + "-fx-border-radius: 4; -fx-background-radius: 4;");
        detailBox.setVisible(false);
        detailBox.setManaged(false);

        root.getChildren().addAll(
                UiSupport.title("Insights"),
                UiSupport.subtitle("Transactions that stand out against your own history in that category."),
                actions, statusLabel, table, detailBox);
    }

    private void showDetail(AIInsight insight) {
        if (insight == null) {
            detailBox.setVisible(false);
            detailBox.setManaged(false);
            return;
        }
        String subject = transactionFor(insight)
                .map(t -> t.getDate() + "  ·  " + t.getDescription()
                        + "  ·  " + UiSupport.money(t.getAbsoluteAmount()))
                .orElse("Transaction no longer exists");
        detailLabel.setText(subject + System.lineSeparator() + System.lineSeparator()
                + insight.getGeneratedText());
        detailBox.getChildren().setAll(UiSupport.subtitle("Full explanation"), detailLabel);
        detailBox.setVisible(true);
        detailBox.setManaged(true);
    }

    private Optional<Transaction> transactionFor(AIInsight insight) {
        return insight.getRelatedTransactionId() == null
                ? Optional.empty()
                : transactionService.findById(insight.getRelatedTransactionId());
    }

    private void scan() {
        UiSupport.guard(() -> {
            YearMonth month = monthBox.getValue() == null ? YearMonth.now() : monthBox.getValue();
            List<AnomalyService.AnomalyFinding> findings = anomalyService.scanMonth(month);
            refresh();
            UiSupport.info("Scan complete", findings.isEmpty()
                    ? "No unusual transactions found in " + month + "."
                    : findings.size() + " unusual transaction(s) found in " + month + ".");
        });
    }

    private void record(UserAction action) {
        AIInsight selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("Nothing selected", "Select an insight in the table first.");
            return;
        }
        UiSupport.guard(() -> {
            anomalyService.recordUserDecision(selected.getId(), action);
            refresh();
        });
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        YearMonth current = YearMonth.now();
        YearMonth selected = monthBox.getValue();
        monthBox.setItems(FXCollections.observableArrayList(
                current.minusMonths(2), current.minusMonths(1), current));
        monthBox.getSelectionModel().select(selected == null ? current : selected);

        table.setItems(FXCollections.observableArrayList(anomalyService.storedAnomalies()));

        statusLabel.setText(aiProvider.isAvailable()
                ? "Explanations are written by " + aiProvider.name()
                    + ". Detection itself is statistical and works offline."
                : "AI is not configured, so explanations are generated from the statistics directly. "
                    + "Detection is unaffected.");
    }
}
