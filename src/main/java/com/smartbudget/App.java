package com.smartbudget;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX entry point for SmartBudget.
 *
 * <p>Stage 0 renders a placeholder window only. The real shell (navigation +
 * screens) is introduced in Stage 4; keeping this minimal until then means the
 * UI layer is built on top of an already-tested service layer rather than
 * growing alongside it.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        Label placeholder = new Label("SmartBudget");
        StackPane root = new StackPane(placeholder);
        root.setAlignment(Pos.CENTER);

        stage.setTitle("SmartBudget");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
