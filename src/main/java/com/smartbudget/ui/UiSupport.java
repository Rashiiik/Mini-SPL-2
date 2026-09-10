package com.smartbudget.ui;

import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.service.ValidationException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.Callback;

import java.text.DecimalFormat;
import java.util.Optional;

/**
 * Shared view helpers: money formatting and the single place where exceptions
 * become dialogs.
 *
 * <p>Keeping error handling here means every screen reports a
 * {@link ValidationException} the same way, and no stack trace ever reaches the
 * user for an ordinary mistake like a blank description.
 */
final class UiSupport {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private UiSupport() {
    }

    static String money(double amount) {
        return MONEY.format(amount);
    }

    static String signedMoney(double amount) {
        return (amount < 0 ? "-" : "+") + MONEY.format(Math.abs(amount));
    }

    static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    static Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        return label;
    }

    /**
     * A table cell that wraps long text over several lines instead of clipping
     * it with an ellipsis.
     *
     * <p>Needed because the AI explanations are full sentences, and a truncated
     * explanation is worse than none — the user cannot tell whether the reason
     * given actually applies. The label's width is bound to the column so the
     * wrap point follows the column as it is resized, and the row grows to fit.
     */
    static <S> Callback<TableColumn<S, String>, TableCell<S, String>> wrappingCell() {
        return column -> new TableCell<>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                // Leave room for the cell's own padding, or the text wraps a
                // character early and the last word drops to its own line.
                label.maxWidthProperty().bind(column.widthProperty().subtract(14));
                setPrefHeight(Region.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null || text.isBlank()) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                label.setText(text);
                setGraphic(label);
                setTooltip(new Tooltip(text));
            }
        };
    }

    static Label subtitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");
        return label;
    }

    /**
     * Runs an action, turning expected failures into a readable dialog.
     *
     * <p>A {@link ValidationException} is a normal outcome of user input and is
     * shown as a warning. A {@link DataAccessException} is not expected during
     * normal use, so it is reported as an error — but still without a stack trace
     * in the user's face.
     */
    static boolean guard(Runnable action) {
        try {
            action.run();
            return true;
        } catch (ValidationException e) {
            show(Alert.AlertType.WARNING, "Please check your input", e.getMessage());
            return false;
        } catch (DataAccessException e) {
            show(Alert.AlertType.ERROR, "Database problem", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            // Last line of defence. An unexpected failure would otherwise be
            // swallowed by the JavaFX thread's default handler, leaving a button
            // that silently does nothing — the hardest kind of bug to report.
            show(Alert.AlertType.ERROR, "Something went wrong",
                    e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return false;
        }
    }

    static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, header, message);
    }

    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("SmartBudget");
        alert.setHeaderText(header);
        alert.setContentText(message);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("SmartBudget");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
