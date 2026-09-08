package com.smartbudget.ui;

import com.smartbudget.model.RecurringRule;
import com.smartbudget.model.RuleStatus;
import com.smartbudget.service.SubscriptionService;
import com.smartbudget.service.SubscriptionService.SubscriptionFinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The Subscription Review workflow.
 *
 * <p>Detection runs on demand rather than on every save: scanning the whole
 * history is not free, and the user should decide when to look, not have rules
 * quietly appear underneath them.
 */
class SubscriptionsView implements RefreshableView {

    private final SubscriptionService subscriptionService;
    private final Runnable onDataChanged;

    private final VBox root = new VBox(12);
    private final TableView<SubscriptionFinding> findingsTable = new TableView<>();
    private final TableView<RecurringRule> rulesTable = new TableView<>();
    private final Label totalLabel = new Label();

    SubscriptionsView(SubscriptionService subscriptionService, Runnable onDataChanged) {
        this.subscriptionService = subscriptionService;
        this.onDataChanged = onDataChanged;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        TableColumn<SubscriptionFinding, String> nameColumn = new TableColumn<>("Subscription");
        nameColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().subscription().pattern()));
        nameColumn.setPrefWidth(180);

        TableColumn<SubscriptionFinding, String> reasonColumn = new TableColumn<>("Flagged as");
        reasonColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().reason().name().replace('_', ' ')));
        reasonColumn.setPrefWidth(140);

        TableColumn<SubscriptionFinding, String> whyColumn = new TableColumn<>("Why");
        whyColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().explanation()));
        whyColumn.setCellFactory(UiSupport.wrappingCell());
        whyColumn.setMinWidth(260);
        whyColumn.setPrefWidth(430);

        findingsTable.getColumns().addAll(nameColumn, reasonColumn, whyColumn);
        findingsTable.setPrefHeight(180);
        findingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        findingsTable.setPlaceholder(new Label(
                "No subscription creep detected. Run a scan to check again."));

        TableColumn<RecurringRule, String> patternColumn = new TableColumn<>("Pattern");
        patternColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescriptionPattern()));
        patternColumn.setPrefWidth(200);

        TableColumn<RecurringRule, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(c -> new SimpleStringProperty(
                subscriptionService.categoryNameFor(c.getValue().getCategoryId())));
        categoryColumn.setPrefWidth(150);

        TableColumn<RecurringRule, String> amountColumn = new TableColumn<>("Expected");
        amountColumn.setCellValueFactory(c -> new SimpleStringProperty(
                UiSupport.money(Math.abs(c.getValue().getExpectedAmount()))));
        amountColumn.setPrefWidth(120);

        TableColumn<RecurringRule, String> frequencyColumn = new TableColumn<>("Frequency");
        frequencyColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFrequency().name().toLowerCase()));
        frequencyColumn.setPrefWidth(100);

        TableColumn<RecurringRule, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name().toLowerCase()));
        statusColumn.setPrefWidth(110);

        rulesTable.getColumns().addAll(patternColumn, categoryColumn, amountColumn,
                frequencyColumn, statusColumn);
        VBox.setVgrow(rulesTable, Priority.ALWAYS);

        Button scanButton = new Button("Scan history");
        scanButton.setOnAction(e -> scan());

        Button keepButton = new Button("Keep (active)");
        keepButton.setOnAction(e -> setStatus(RuleStatus.ACTIVE));

        Button cancelButton = new Button("Mark cancelled");
        cancelButton.setOnAction(e -> setStatus(RuleStatus.CANCELLED));

        Button ignoreButton = new Button("Ignore");
        ignoreButton.setOnAction(e -> setStatus(RuleStatus.IGNORED));

        HBox actions = new HBox(8, scanButton, new Label("   Selected rule:"),
                keepButton, cancelButton, ignoreButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        totalLabel.setStyle("-fx-font-size: 14px;");

        root.getChildren().addAll(
                UiSupport.title("Subscriptions"),
                UiSupport.subtitle("Repeating charges found in your history, and the ones worth reviewing."),
                actions, totalLabel,
                UiSupport.subtitle("Flagged for review"), findingsTable,
                UiSupport.subtitle("All recurring rules"), rulesTable);
    }

    private void scan() {
        UiSupport.guard(() -> {
            int created = subscriptionService.saveDetectedRules();
            int flagged = subscriptionService.flagCreep();
            refresh();
            onDataChanged.run();
            UiSupport.info("Scan complete", String.format(
                    "%d new recurring rule(s) detected, %d flagged for review.", created, flagged));
        });
    }

    private void setStatus(RuleStatus status) {
        RecurringRule selected = rulesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("Nothing selected", "Select a rule in the lower table first.");
            return;
        }
        UiSupport.guard(() -> {
            subscriptionService.updateStatus(selected.getId(), status);
            refresh();
            onDataChanged.run();
        });
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        List<SubscriptionFinding> findings = subscriptionService.findCreep();
        findingsTable.setItems(FXCollections.observableArrayList(findings));
        rulesTable.setItems(FXCollections.observableArrayList(subscriptionService.allRules()));
        totalLabel.setText(String.format(
                "Detected recurring charges cost about %s per month across %d subscription(s).",
                UiSupport.money(subscriptionService.monthlyTotal()),
                subscriptionService.detect().size()));
    }
}
