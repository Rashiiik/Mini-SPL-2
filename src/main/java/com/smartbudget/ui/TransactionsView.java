package com.smartbudget.ui;

import com.smartbudget.model.Account;
import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.service.AccountService;
import com.smartbudget.service.TransactionService;
import com.smartbudget.service.ValidationException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Categorise &amp; Alert workflow's screen: enter a transaction, see the
 * strategy's suggested category, override it if wrong, save.
 */
class TransactionsView implements RefreshableView {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryDao categoryDao;
    private final Runnable onDataChanged;

    private final VBox root = new VBox(12);
    private final TableView<Transaction> table = new TableView<>();

    private final ComboBox<Account> accountBox = new ComboBox<>();
    private final ComboBox<Category> categoryBox = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final TextField descriptionField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final CheckBox recurringBox = new CheckBox("Recurring");
    private final Label suggestionLabel = new Label();

    private final ComboBox<Account> filterAccount = new ComboBox<>();
    private final DatePicker filterFrom = new DatePicker();
    private final DatePicker filterTo = new DatePicker();

    private Map<Integer, String> categoryNames = new HashMap<>();
    private Map<Integer, String> accountNames = new HashMap<>();

    TransactionsView(TransactionService transactionService, AccountService accountService,
                     CategoryDao categoryDao, Runnable onDataChanged) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryDao = categoryDao;
        this.onDataChanged = onDataChanged;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        TableColumn<Transaction, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDate().toString()));
        dateColumn.setPrefWidth(100);

        TableColumn<Transaction, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescription()));
        descriptionColumn.setPrefWidth(240);

        TableColumn<Transaction, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoryId() == null
                        ? "Uncategorised"
                        : categoryNames.getOrDefault(c.getValue().getCategoryId(), "?")));
        categoryColumn.setPrefWidth(140);

        TableColumn<Transaction, String> accountColumn = new TableColumn<>("Account");
        accountColumn.setCellValueFactory(c -> new SimpleStringProperty(
                accountNames.getOrDefault(c.getValue().getAccountId(), "?")));
        accountColumn.setPrefWidth(150);

        TableColumn<Transaction, String> amountColumn = new TableColumn<>("Amount");
        amountColumn.setCellValueFactory(c ->
                new SimpleStringProperty(UiSupport.signedMoney(c.getValue().getAmount())));
        amountColumn.setPrefWidth(120);

        table.getColumns().addAll(dateColumn, descriptionColumn, categoryColumn,
                accountColumn, amountColumn);
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> loadIntoForm(selected));
        VBox.setVgrow(table, Priority.ALWAYS);

        amountField.setPromptText("Amount (negative for spending)");
        amountField.setPrefWidth(180);
        descriptionField.setPromptText("Description");
        descriptionField.setPrefWidth(220);
        // Suggest a category as the user types, so the strategy is visible
        // before saving rather than silently applied afterwards.
        descriptionField.textProperty().addListener((obs, old, text) -> updateSuggestion(text));
        categoryBox.setPromptText("Category (optional)");
        suggestionLabel.setStyle("-fx-text-fill: #2a6f2a;");

        Button saveButton = new Button("Add");
        saveButton.setOnAction(e -> addTransaction());
        Button updateButton = new Button("Update selected");
        updateButton.setOnAction(e -> updateSelected());
        Button deleteButton = new Button("Delete selected");
        deleteButton.setOnAction(e -> deleteSelected());
        Button clearButton = new Button("Clear form");
        clearButton.setOnAction(e -> clearForm());

        FlowPane form = new FlowPane(8, 8,
                new Label("Date"), datePicker,
                new Label("Account"), accountBox,
                new Label("Amount"), amountField,
                new Label("Description"), descriptionField,
                new Label("Category"), categoryBox,
                recurringBox, saveButton, updateButton, deleteButton, clearButton);
        form.setAlignment(Pos.CENTER_LEFT);

        Button applyFilter = new Button("Apply filter");
        applyFilter.setOnAction(e -> applyFilter());
        Button clearFilter = new Button("Show all");
        clearFilter.setOnAction(e -> {
            filterAccount.getSelectionModel().clearSelection();
            filterFrom.setValue(null);
            filterTo.setValue(null);
            refresh();
        });
        filterAccount.setPromptText("Any account");
        filterFrom.setPromptText("From");
        filterTo.setPromptText("To");

        HBox filters = new HBox(8, new Label("Filter:"), filterAccount,
                new Label("From"), filterFrom, new Label("To"), filterTo, applyFilter, clearFilter);
        filters.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                UiSupport.title("Transactions"),
                UiSupport.subtitle("Categories are suggested by the "
                        + transactionService.strategyName()
                        + " strategy and can always be overridden."),
                form, suggestionLabel, filters, table);
    }

    private void updateSuggestion(String description) {
        if (description == null || description.isBlank()) {
            suggestionLabel.setText("");
            return;
        }
        Transaction probe = new Transaction(null, 0, null, -1, LocalDate.now(), description, false);
        transactionService.suggestCategory(probe).ifPresentOrElse(
                category -> suggestionLabel.setText("Suggested category: " + category.getName()),
                () -> suggestionLabel.setText("No category suggestion for this description."));
    }

    private void addTransaction() {
        UiSupport.guard(() -> {
            Transaction transaction = fromForm(null);
            transactionService.create(transaction);
            clearForm();
            refresh();
            onDataChanged.run();
        });
    }

    private void updateSelected() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("Nothing selected", "Select a transaction in the table first.");
            return;
        }
        UiSupport.guard(() -> {
            transactionService.update(fromForm(selected.getId()));
            clearForm();
            refresh();
            onDataChanged.run();
        });
    }

    private void deleteSelected() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("Nothing selected", "Select a transaction in the table first.");
            return;
        }
        if (UiSupport.confirm("Delete this transaction?",
                selected.getDescription() + " (" + UiSupport.signedMoney(selected.getAmount()) + ")")) {
            UiSupport.guard(() -> {
                transactionService.delete(selected.getId());
                clearForm();
                refresh();
                onDataChanged.run();
            });
        }
    }

    private Transaction fromForm(Integer id) {
        Account account = accountBox.getValue();
        if (account == null) {
            throw new ValidationException("Please choose an account.");
        }
        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException("Enter a valid amount, for example -450 for spending.");
        }
        Category category = categoryBox.getValue();
        return new Transaction(id, account.getId(),
                category == null ? null : category.getId(),
                amount, datePicker.getValue(), descriptionField.getText(),
                recurringBox.isSelected());
    }

    private void loadIntoForm(Transaction transaction) {
        if (transaction == null) {
            return;
        }
        accountBox.getItems().stream()
                .filter(a -> a.getId() == transaction.getAccountId())
                .findFirst().ifPresent(accountBox.getSelectionModel()::select);
        if (transaction.getCategoryId() == null) {
            categoryBox.getSelectionModel().clearSelection();
        } else {
            categoryBox.getItems().stream()
                    .filter(c -> c.getId().equals(transaction.getCategoryId()))
                    .findFirst().ifPresent(categoryBox.getSelectionModel()::select);
        }
        amountField.setText(String.valueOf(transaction.getAmount()));
        descriptionField.setText(transaction.getDescription());
        datePicker.setValue(transaction.getDate());
        recurringBox.setSelected(transaction.isRecurring());
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        categoryBox.getSelectionModel().clearSelection();
        recurringBox.setSelected(false);
        datePicker.setValue(LocalDate.now());
        table.getSelectionModel().clearSelection();
        suggestionLabel.setText("");
    }

    private void applyFilter() {
        UiSupport.guard(() -> {
            List<Transaction> results;
            if (filterFrom.getValue() != null || filterTo.getValue() != null) {
                LocalDate from = filterFrom.getValue() == null ? LocalDate.of(1970, 1, 1) : filterFrom.getValue();
                LocalDate to = filterTo.getValue() == null ? LocalDate.now() : filterTo.getValue();
                results = transactionService.findBetween(from, to);
            } else {
                results = transactionService.findAll();
            }
            Account account = filterAccount.getValue();
            if (account != null) {
                results = results.stream().filter(t -> t.getAccountId() == account.getId()).toList();
            }
            table.setItems(FXCollections.observableArrayList(results));
        });
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        List<Account> accounts = accountService.findAll();
        List<Category> categories = categoryDao.findAll();

        accountNames = new HashMap<>();
        accounts.forEach(a -> accountNames.put(a.getId(), a.getName()));
        categoryNames = new HashMap<>();
        categories.forEach(c -> categoryNames.put(c.getId(), c.getName()));

        Account selectedAccount = accountBox.getValue();
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        filterAccount.setItems(FXCollections.observableArrayList(accounts));
        categoryBox.setItems(FXCollections.observableArrayList(categories));
        if (selectedAccount != null) {
            accountBox.getSelectionModel().select(selectedAccount);
        } else if (!accounts.isEmpty()) {
            accountBox.getSelectionModel().selectFirst();
        }

        table.setItems(FXCollections.observableArrayList(transactionService.findAll()));
    }
}
