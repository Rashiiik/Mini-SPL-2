package com.smartbudget.ui;

import javafx.scene.Node;

/**
 * A screen that can rebuild its contents from the database.
 *
 * <p>Editing a transaction changes what the dashboard, budgets and reports
 * screens should show. Rather than have each screen listen for every possible
 * change, the shell refreshes whichever screen the user switches to — simple,
 * and correct for a single-user desktop application.
 */
interface RefreshableView {

    Node node();

    void refresh();
}
