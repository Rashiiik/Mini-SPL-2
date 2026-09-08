package com.smartbudget.ui;

import com.smartbudget.model.Account;
import com.smartbudget.model.AccountType;
import com.smartbudget.service.AccountService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Account CRUD. Creation goes through {@code AccountFactory}, never {@code new Account}. */
class AccountsView implements RefreshableView {

    private final AccountService accountService;
    private final Runnable onDataChanged;

    private final VBox root = new VBox(12);
    private final TableView<Account> table = new TableView<>();

    private final TextField nameField = new TextField();
    private final ComboBox<AccountType> typeBox = new ComboBox<>();
    private final TextField openingField = new TextField();
    private final TextField currencyField = new TextField("BDT");

    AccountsView(AccountService accountService, Runnable onDataChanged) {
        this.accountService = accountService;
        this.onDataChanged = onDataChanged;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        TableColumn<Account, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(220);

        TableColumn<Account, AccountType> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(120);

        TableColumn<Account, String> balanceColumn = new TableColumn<>("Balance");
        balanceColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        UiSupport.money(cell.getValue().getBalance())));
        balanceColumn.setPrefWidth(140);

        TableColumn<Account, String> meaningColumn = new TableColumn<>("Balance means");
        meaningColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getType() == AccountType.CREDIT ? "Owed" : "Available"));
        meaningColumn.setPrefWidth(120);

        TableColumn<Account, String> currencyColumn = new TableColumn<>("Currency");
        currencyColumn.setCellValueFactory(new PropertyValueFactory<>("currency"));

        table.getColumns().addAll(nameColumn, typeColumn, balanceColumn, meaningColumn, currencyColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        typeBox.setItems(FXCollections.observableArrayList(AccountType.values()));
        typeBox.getSelectionModel().select(AccountType.CHECKING);
        nameField.setPromptText("Account name");
        openingField.setPromptText("Opening balance");
        openingField.setText("0");
        currencyField.setPrefWidth(70);

        Button addButton = new Button("Add account");
        addButton.setOnAction(e -> addAccount());

        Button renameButton = new Button("Rename selected");
        renameButton.setOnAction(e -> renameSelected());

        Button deleteButton = new Button("Delete selected");
        deleteButton.setOnAction(e -> deleteSelected());

        HBox form = new HBox(8, new Label("Name"), nameField, new Label("Type"), typeBox,
                new Label("Opening"), openingField, new Label("Currency"), currencyField, addButton);
        form.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        HBox actions = new HBox(8, renameButton, deleteButton);

        root.getChildren().addAll(
                UiSupport.title("Accounts"),
                UiSupport.subtitle("A credit card balance is the amount owed, so spending increases it."),
                form, actions, table);
    }

    private void addAccount() {
        UiSupport.guard(() -> {
            double opening = parseAmount(openingField.getText());
            accountService.create(typeBox.getValue(), nameField.getText(), opening,
                    currencyField.getText());
            nameField.clear();
            openingField.setText("0");
            refresh();
            onDataChanged.run();
        });
    }

    private void renameSelected() {
        Account selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("No account selected", "Select an account in the table first.");
            return;
        }
        javafx.scene.control.TextInputDialog dialog =
                new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("SmartBudget");
        dialog.setHeaderText("Rename account");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> UiSupport.guard(() -> {
            accountService.rename(selected.getId(), newName);
            refresh();
            onDataChanged.run();
        }));
    }

    private void deleteSelected() {
        Account selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.info("No account selected", "Select an account in the table first.");
            return;
        }
        int affected = accountService.transactionCount(selected.getId());
        boolean confirmed = UiSupport.confirm("Delete " + selected.getName() + "?",
                affected == 0
                        ? "This account has no transactions."
                        : "This will also delete " + affected + " transaction(s). This cannot be undone.");
        if (confirmed) {
            UiSupport.guard(() -> {
                accountService.delete(selected.getId());
                refresh();
                onDataChanged.run();
            });
        }
    }

    private double parseAmount(String text) {
        try {
            return Double.parseDouble(text == null || text.isBlank() ? "0" : text.trim());
        } catch (NumberFormatException e) {
            throw new com.smartbudget.service.ValidationException(
                    "\"" + text + "\" is not a valid amount.");
        }
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        table.setItems(FXCollections.observableArrayList(accountService.findAll()));
    }
}
