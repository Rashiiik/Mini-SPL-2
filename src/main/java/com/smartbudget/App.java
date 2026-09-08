package com.smartbudget;

import com.smartbudget.persistence.DataAccessException;
import com.smartbudget.ui.AppContext;
import com.smartbudget.ui.MainView;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 *
 * <p>Builds the object graph once through {@link AppContext} and hands it to
 * {@link MainView}. Nothing else in the application constructs a DAO or a
 * service, so the wiring stays in one readable place.
 */
public class App extends Application {

    private AppContext context;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        try {
            context = new AppContext();
        } catch (DataAccessException e) {
            // Without a database there is no application, so report clearly and
            // exit rather than opening a window that cannot do anything.
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("SmartBudget");
            alert.setHeaderText("Could not open the database");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            return;
        }

        mainView = new MainView(context);

        stage.setTitle("SmartBudget — Personal Finance Manager");
        stage.setScene(mainView.createScene());
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    @Override
    public void stop() {
        if (mainView != null) {
            mainView.dispose();
        }
        if (context != null) {
            context.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
