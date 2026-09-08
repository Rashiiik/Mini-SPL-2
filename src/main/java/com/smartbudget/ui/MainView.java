package com.smartbudget.ui;

import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The application shell: five tabs, each a {@link RefreshableView}.
 *
 * <p>Switching to a tab refreshes it. Saving a transaction affects balances,
 * budgets and reports, so rather than have every screen subscribe to every
 * change, the shell reloads whatever the user is looking at. For a single-user
 * desktop application that is both correct and far simpler to reason about.
 */
public class MainView {

    private final AppContext context;
    private final TabPane tabPane = new TabPane();
    private final Map<Tab, RefreshableView> views = new LinkedHashMap<>();

    private final DashboardView dashboard;

    public MainView(AppContext context) {
        this.context = context;

        dashboard = new DashboardView(
                context.accountService(), context.budgetService(), context.reportService());

        // The dashboard is the Observer's subscriber. Registered once, here,
        // so the wiring is visible rather than hidden inside the view.
        context.budgetEventBus().subscribe(dashboard);

        AccountsView accounts = new AccountsView(context.accountService(), this::refreshAll);
        TransactionsView transactions = new TransactionsView(
                context.transactionService(), context.accountService(),
                context.categoryDao(), this::refreshAll);
        BudgetsView budgets = new BudgetsView(
                context.budgetService(), context.categoryDao(), this::refreshAll);
        ReportsView reports = new ReportsView(context.reportService());

        addTab("Dashboard", dashboard);
        addTab("Accounts", accounts);
        addTab("Transactions", transactions);
        addTab("Budgets", budgets);
        addTab("Reports", reports);

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            RefreshableView view = views.get(selected);
            if (view != null) {
                view.refresh();
            }
        });

        refreshAll();
    }

    private void addTab(String title, RefreshableView view) {
        ScrollPane scroller = new ScrollPane(view.node());
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);

        Tab tab = new Tab(title, scroller);
        views.put(tab, view);
        tabPane.getTabs().add(tab);
    }

    /** Reloads every screen. Called after any change that could affect more than one. */
    private void refreshAll() {
        views.values().forEach(RefreshableView::refresh);
    }

    public Scene createScene() {
        return new Scene(tabPane, 1100, 720);
    }

    /** Detaches the dashboard from the event bus so nothing is notified after shutdown. */
    public void dispose() {
        context.budgetEventBus().unsubscribe(dashboard);
    }
}
