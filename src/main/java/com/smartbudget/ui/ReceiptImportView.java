package com.smartbudget.ui;

import com.smartbudget.model.Account;
import com.smartbudget.model.Category;
import com.smartbudget.model.Transaction;
import com.smartbudget.persistence.dao.CategoryDao;
import com.smartbudget.service.AccountService;
import com.smartbudget.service.ReceiptService;
import com.smartbudget.service.ReceiptService.ReceiptExtraction;
import com.smartbudget.service.TransactionService;
import com.smartbudget.service.ValidationException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The Receipt Import workflow: choose a photo, let the model read it, correct
 * what it got wrong, then save.
 *
 * <p>The extracted values land in an ordinary editable form and are saved
 * through {@link TransactionService#create}, so an imported transaction is
 * categorised and checked against budgets exactly like a typed one. Nothing is
 * written until the user presses Save.
 */
class ReceiptImportView implements RefreshableView {

    private final ReceiptService receiptService;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryDao categoryDao;
    private final Runnable onDataChanged;

    private final VBox root = new VBox(12);
    private final ImageView preview = new ImageView();
    private final Label fileLabel = new Label("No image chosen.");
    private final Label statusLabel = new Label();
    private final Label rawLabel = new Label();

    private final TextField descriptionField = new TextField();
    private final TextField amountField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final ComboBox<Account> accountBox = new ComboBox<>();
    private final ComboBox<Category> categoryBox = new ComboBox<>();

    private Path chosenFile;

    ReceiptImportView(ReceiptService receiptService, TransactionService transactionService,
                      AccountService accountService, CategoryDao categoryDao,
                      Runnable onDataChanged) {
        this.receiptService = receiptService;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryDao = categoryDao;
        this.onDataChanged = onDataChanged;
        build();
    }

    private void build() {
        root.setPadding(new Insets(16));

        preview.setPreserveRatio(true);
        preview.setFitWidth(320);
        preview.setFitHeight(320);

        Button chooseButton = new Button("Choose receipt image…");
        chooseButton.setOnAction(e -> chooseFile());

        Button scanButton = new Button("Read receipt");
        scanButton.setOnAction(e -> scan());

        Button saveButton = new Button("Save transaction");
        saveButton.setOnAction(e -> save());

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clear());

        amountField.setPromptText("Total, as a positive number");
        descriptionField.setPromptText("Merchant");
        categoryBox.setPromptText("Category (optional)");

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Merchant"), descriptionField);
        form.addRow(1, new Label("Amount"), amountField);
        form.addRow(2, new Label("Date"), datePicker);
        form.addRow(3, new Label("Account"), accountBox);
        form.addRow(4, new Label("Category"), categoryBox);

        statusLabel.setWrapText(true);
        rawLabel.setWrapText(true);
        rawLabel.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");

        HBox buttons = new HBox(8, chooseButton, scanButton, saveButton, clearButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox formSide = new VBox(10,
                UiSupport.subtitle("Check these before saving"), form, rawLabel);
        HBox body = new HBox(20, new VBox(6, preview, fileLabel), formSide);

        root.getChildren().addAll(
                UiSupport.title("Receipt Import"),
                UiSupport.subtitle("Everything read from the photo is a suggestion. "
                        + "Correct anything wrong before saving."),
                buttons, statusLabel, body);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a receipt image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));

        File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        chosenFile = file.toPath();
        fileLabel.setText(file.getName());
        preview.setImage(new Image(file.toURI().toString(), 320, 0, true, true));
        statusLabel.setText("Image loaded. Press \"Read receipt\" to extract the details.");
        rawLabel.setText("");
    }

    private void scan() {
        if (chosenFile == null) {
            UiSupport.info("No image chosen", "Choose a receipt image first.");
            return;
        }
        UiSupport.guard(() -> {
            Optional<ReceiptExtraction> extraction = receiptService.extract(chosenFile);
            if (extraction.isEmpty()) {
                statusLabel.setText("The receipt could not be read. "
                        + "Fill the fields in manually, or try a clearer photo.");
                return;
            }
            applyToForm(extraction.get());
        });
    }

    private void applyToForm(ReceiptExtraction extraction) {
        if (extraction.merchant() != null) {
            descriptionField.setText(extraction.merchant());
        }
        if (extraction.amount() != null) {
            amountField.setText(String.valueOf(extraction.amount()));
        }
        if (extraction.date() != null) {
            datePicker.setValue(extraction.date());
        }

        // Offer the categorisation strategy's suggestion for the merchant name,
        // so an imported receipt gets the same treatment as a typed entry.
        if (extraction.merchant() != null) {
            Transaction probe = new Transaction(null, 0, null, -1,
                    LocalDate.now(), extraction.merchant(), false);
            transactionService.suggestCategory(probe).ifPresent(category ->
                    categoryBox.getItems().stream()
                            .filter(c -> c.getId().equals(category.getId()))
                            .findFirst()
                            .ifPresent(categoryBox.getSelectionModel()::select));
        }

        statusLabel.setText(extraction.isComplete()
                ? "Read successfully. Check the values below, then save."
                : "Some fields could not be read. Please fill in the blanks below.");
        rawLabel.setText("Model returned: " + extraction.rawAnswer());
    }

    private void save() {
        UiSupport.guard(() -> {
            Account account = accountBox.getValue();
            if (account == null) {
                throw new ValidationException("Please choose an account.");
            }
            double amount;
            try {
                amount = Double.parseDouble(amountField.getText().trim());
            } catch (NumberFormatException | NullPointerException e) {
                throw new ValidationException("Enter the receipt total as a number.");
            }
            if (amount <= 0) {
                throw new ValidationException("The receipt total should be a positive number.");
            }

            Category category = categoryBox.getValue();
            // A receipt is always money going out, so the sign is applied here
            // rather than asking the user to type a minus.
            Transaction transaction = new Transaction(null, account.getId(),
                    category == null ? null : category.getId(),
                    -amount, datePicker.getValue(), descriptionField.getText(), false);

            transactionService.create(transaction);
            clear();
            onDataChanged.run();
            UiSupport.info("Saved", "The transaction was added from the receipt.");
        });
    }

    private void clear() {
        chosenFile = null;
        preview.setImage(null);
        fileLabel.setText("No image chosen.");
        descriptionField.clear();
        amountField.clear();
        categoryBox.getSelectionModel().clearSelection();
        datePicker.setValue(LocalDate.now());
        statusLabel.setText("");
        rawLabel.setText("");
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        List<Account> accounts = accountService.findAll();
        Account selected = accountBox.getValue();
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        if (selected != null) {
            accountBox.getSelectionModel().select(selected);
        } else if (!accounts.isEmpty()) {
            accountBox.getSelectionModel().selectFirst();
        }
        categoryBox.setItems(FXCollections.observableArrayList(categoryDao.findAll()));

        if (!receiptService.isAvailable() && statusLabel.getText().isEmpty()) {
            statusLabel.setText("Receipt scanning needs an AI key in config.properties. "
                    + "You can still fill the form in by hand and save.");
        }
    }
}
