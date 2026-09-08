package com.smartbudget.ui;

import com.smartbudget.model.Category;
import com.smartbudget.pattern.observer.AlertSeverity;
import com.smartbudget.pattern.observer.BudgetAlert;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.service.BudgetService;
import com.smartbudget.service.ValidationException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;

/** Set monthly targets per category and see actual spend against them. */
class BudgetsView implements RefreshableView {

    private final BudgetService budgetService;
    private final CategoryDao categoryDao;
    private final Runnable onDataChanged;

    private final VBox root = new VBox(12);
    private final TableView<BudgetAlert> table = new TableView<>();
    private final ComboBox<Category> categoryBox = new ComboBox<>();
    private final TextField targetField = new TextField();
    private final ComboBox<YearMonth> monthBox = new ComboBox<>();

    BudgetsView(BudgetService budgetService, CategoryDao categoryDao, Runnable onDataChanged) {
        this.budgetService = budgetService;
        this.categoryDao = categoryDao;
        this.onDataChanged = onDataChanged;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        TableColumn<BudgetAlert, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoryName()));
        categoryColumn.setPrefWidth(180);

        TableColumn<BudgetAlert, String> targetColumn = new TableColumn<>("Target");
        targetColumn.setCellValueFactory(c ->
                new SimpleStringProperty(UiSupport.money(c.getValue().target())));
        targetColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, String> spentColumn = new TableColumn<>("Spent");
        spentColumn.setCellValueFactory(c ->
                new SimpleStringProperty(UiSupport.money(c.getValue().spent())));
        spentColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, String> remainingColumn = new TableColumn<>("Remaining");
        remainingColumn.setCellValueFactory(c -> new SimpleStringProperty(
                UiSupport.money(c.getValue().target() - c.getValue().spent())));
        remainingColumn.setPrefWidth(120);

        TableColumn<BudgetAlert, BudgetAlert> progressColumn = new TableColumn<>("Progress");
        progressColumn.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        progressColumn.setCellFactory(column -> progressCell());
        progressColumn.setPrefWidth(220);

        table.getColumns().addAll(categoryColumn, targetColumn, spentColumn,
                remainingColumn, progressColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        targetField.setPromptText("Monthly target");
        Button setButton = new Button("Set target");
        setButton.setOnAction(e -> setTarget());
        monthBox.setOnAction(e -> refreshTable());

        HBox form = new HBox(8, new Label("Month"), monthBox,
                new Label("Category"), categoryBox,
                new Label("Target"), targetField, setButton);
        form.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                UiSupport.title("Budgets"),
                UiSupport.subtitle("Setting a target for a category that already has one replaces it."),
                form, table);
    }

    /** Renders the utilisation bar, coloured by the same severity the alerts use. */
    private TableCell<BudgetAlert, BudgetAlert> progressCell() {
        return new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label label = new Label();
            private final HBox box = new HBox(8, bar, label);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                bar.setPrefWidth(120);
            }

            @Override
            protected void updateItem(BudgetAlert alert, boolean empty) {
                super.updateItem(alert, empty);
                if (empty || alert == null) {
                    setGraphic(null);
                    return;
                }
                double utilisation = alert.utilisation();
                bar.setProgress(Math.min(utilisation, 1.0));
                bar.setStyle("-fx-accent: " + colourFor(alert.severity()) + ";");
                label.setText(UiSupport.percent(utilisation));
                setGraphic(box);
            }
        };
    }

    private static String colourFor(AlertSeverity severity) {
        return switch (severity) {
            case EXCEEDED -> "#c0392b";
            case WARNING -> "#e67e22";
            case OK -> "#27ae60";
        };
    }

    private void setTarget() {
        UiSupport.guard(() -> {
            Category category = categoryBox.getValue();
            if (category == null) {
                throw new ValidationException("Please choose a category.");
            }
            double target;
            try {
                target = Double.parseDouble(targetField.getText().trim());
            } catch (NumberFormatException | NullPointerException e) {
                throw new ValidationException("Enter a valid target amount.");
            }
            budgetService.setTarget(category.getId(), monthBox.getValue(), target);
            targetField.clear();
            refreshTable();
            onDataChanged.run();
        });
    }

    private void refreshTable() {
        YearMonth month = monthBox.getValue() == null ? YearMonth.now() : monthBox.getValue();
        table.setItems(FXCollections.observableArrayList(budgetService.statusFor(month)));
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
                current.minusMonths(2), current.minusMonths(1), current,
                current.plusMonths(1), current.plusMonths(2));
        monthBox.setItems(FXCollections.observableArrayList(months));
        monthBox.getSelectionModel().select(selected == null ? current : selected);

        Category selectedCategory = categoryBox.getValue();
        categoryBox.setItems(FXCollections.observableArrayList(categoryDao.findAll()));
        if (selectedCategory != null) {
            categoryBox.getSelectionModel().select(selectedCategory);
        }

        refreshTable();
    }
}
