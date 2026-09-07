-- SmartBudget schema
--
-- Money convention: `amount` is SIGNED. Negative = money out (expense),
-- positive = money in (income). Every consumer of this schema relies on that,
-- so it is enforced with CHECK constraints rather than left to convention.
--
-- Dates are stored as ISO-8601 text ('YYYY-MM-DD'), which sorts correctly
-- lexicographically in SQLite and maps cleanly to java.time.LocalDate.

CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER NOT NULL PRIMARY KEY,
    applied_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS categories (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    name  TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    type  TEXT    NOT NULL CHECK (type IN ('expense', 'income'))
);

CREATE TABLE IF NOT EXISTS accounts (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    type     TEXT    NOT NULL CHECK (type IN ('checking', 'savings', 'credit')),
    balance  REAL    NOT NULL DEFAULT 0,
    currency TEXT    NOT NULL DEFAULT 'BDT' CHECK (length(currency) = 3)
);

CREATE TABLE IF NOT EXISTS transactions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id   INTEGER NOT NULL,
    category_id  INTEGER,
    amount       REAL    NOT NULL CHECK (amount <> 0),
    date         TEXT    NOT NULL CHECK (date IS strftime('%Y-%m-%d', date)),
    description  TEXT    NOT NULL CHECK (length(trim(description)) > 0),
    is_recurring INTEGER NOT NULL DEFAULT 0 CHECK (is_recurring IN (0, 1)),
    FOREIGN KEY (account_id)  REFERENCES accounts(id)   ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- One target per category per month; the UNIQUE constraint is what makes
-- "set budget" an upsert rather than a duplicate-prone insert.
CREATE TABLE IF NOT EXISTS budgets (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id   INTEGER NOT NULL,
    month         TEXT    NOT NULL CHECK (month IS strftime('%Y-%m', month || '-01')),
    target_amount REAL    NOT NULL CHECK (target_amount > 0),
    UNIQUE (category_id, month),
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS recurring_rules (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    description_pattern TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    category_id         INTEGER,
    expected_amount     REAL    NOT NULL CHECK (expected_amount <> 0),
    frequency           TEXT    NOT NULL CHECK (frequency IN ('weekly', 'monthly', 'yearly')),
    status              TEXT    NOT NULL DEFAULT 'active'
                                CHECK (status IN ('active', 'flagged', 'ignored', 'cancelled')),
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- An insight points at exactly one subject: a transaction OR a report month.
-- The CHECK enforces that instead of leaving both columns nullable and unchecked.
CREATE TABLE IF NOT EXISTS ai_insights (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    related_transaction_id INTEGER,
    related_report_month   TEXT,
    kind                   TEXT    NOT NULL
                                   CHECK (kind IN ('anomaly', 'report', 'subscription')),
    generated_text         TEXT    NOT NULL,
    user_action            TEXT    NOT NULL DEFAULT 'pending'
                                   CHECK (user_action IN ('pending', 'accepted', 'edited', 'rejected')),
    created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
    CHECK ((related_transaction_id IS NULL) <> (related_report_month IS NULL)),
    FOREIGN KEY (related_transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tx_account_date  ON transactions (account_id, date);
CREATE INDEX IF NOT EXISTS idx_tx_category_date ON transactions (category_id, date);
CREATE INDEX IF NOT EXISTS idx_budget_month     ON budgets (month);
