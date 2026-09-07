-- Seed data. Dates are computed relative to the current month so the sample
-- data is always "recent" whenever the project is cloned and demonstrated,
-- rather than going stale against hard-coded dates.

INSERT INTO categories (name, type) VALUES
    ('Groceries',     'expense'),
    ('Transport',     'expense'),
    ('Dining',        'expense'),
    ('Utilities',     'expense'),
    ('Rent',          'expense'),
    ('Entertainment', 'expense'),
    ('Subscriptions', 'expense'),
    ('Health',        'expense'),
    ('Shopping',      'expense'),
    ('Salary',        'income'),
    ('Other',         'expense');

INSERT INTO accounts (name, type, balance, currency) VALUES
    ('Main Checking',  'checking', 85000.00,  'BDT'),
    ('Emergency Fund', 'savings',  150000.00, 'BDT'),
    ('City Bank Card', 'credit',   12500.00,  'BDT');

-- Transaction dates are built from date('now','start of month', <month>, <day>)
-- so the three seeded months are always the current month and the two before it.
INSERT INTO transactions (account_id, category_id, amount, date, description, is_recurring)
SELECT a.id, c.id, v.amount,
       date('now', 'start of month', v.month_offset, v.day_offset),
       v.description, v.is_recurring
FROM (
    SELECT 'Main Checking' AS acc, 'Salary' AS cat, 65000.0 AS amount, '-2 months' AS month_offset, '+0 days' AS day_offset, 'Monthly salary' AS description, 1 AS is_recurring
    UNION ALL SELECT 'Main Checking',  'Rent',          -22000.0, '-2 months', '+1 days',  'House rent',             1
    UNION ALL SELECT 'Main Checking',  'Groceries',      -3200.0, '-2 months', '+3 days',  'Shwapno grocery run',    0
    UNION ALL SELECT 'Main Checking',  'Transport',       -450.0, '-2 months', '+5 days',  'Uber to office',         0
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -599.0, '-2 months', '+7 days',  'Netflix subscription',   1
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -350.0, '-2 months', '+8 days',  'Spotify Premium',        1
    UNION ALL SELECT 'Main Checking',  'Dining',         -1250.0, '-2 months', '+12 days', 'Dinner at Sultans Dine', 0
    UNION ALL SELECT 'Main Checking',  'Utilities',      -1800.0, '-2 months', '+15 days', 'Electricity bill',       1
    UNION ALL SELECT 'Main Checking',  'Groceries',      -2750.0, '-2 months', '+18 days', 'Meena Bazar weekly',     0
    UNION ALL SELECT 'City Bank Card', 'Entertainment',   -900.0, '-2 months', '+22 days', 'Star Cineplex tickets',  0

    UNION ALL SELECT 'Main Checking',  'Salary',         65000.0, '-1 month',  '+0 days',  'Monthly salary',         1
    UNION ALL SELECT 'Main Checking',  'Rent',          -22000.0, '-1 month',  '+1 days',  'House rent',             1
    UNION ALL SELECT 'Main Checking',  'Groceries',      -3600.0, '-1 month',  '+4 days',  'Shwapno grocery run',    0
    UNION ALL SELECT 'Main Checking',  'Transport',       -520.0, '-1 month',  '+6 days',  'Uber to airport',        0
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -599.0, '-1 month',  '+7 days',  'Netflix subscription',   1
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -350.0, '-1 month',  '+8 days',  'Spotify Premium',        1
    UNION ALL SELECT 'Main Checking',  'Dining',         -1400.0, '-1 month',  '+11 days', 'Lunch with team',        0
    UNION ALL SELECT 'Main Checking',  'Utilities',      -1950.0, '-1 month',  '+15 days', 'Electricity bill',       1
    UNION ALL SELECT 'Main Checking',  'Health',         -2200.0, '-1 month',  '+17 days', 'Pharmacy - monthly meds',0
    UNION ALL SELECT 'Main Checking',  'Groceries',      -2900.0, '-1 month',  '+20 days', 'Meena Bazar weekly',     0
    UNION ALL SELECT 'City Bank Card', 'Shopping',       -4500.0, '-1 month',  '+24 days', 'Winter jacket',          0

    UNION ALL SELECT 'Main Checking',  'Salary',         65000.0, '+0 months', '+0 days',  'Monthly salary',         1
    UNION ALL SELECT 'Main Checking',  'Rent',          -22000.0, '+0 months', '+1 days',  'House rent',             1
    UNION ALL SELECT 'Main Checking',  'Groceries',      -3400.0, '+0 months', '+3 days',  'Shwapno grocery run',    0
    UNION ALL SELECT 'Main Checking',  'Transport',       -480.0, '+0 months', '+5 days',  'Uber to office',         0
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -599.0, '+0 months', '+7 days',  'Netflix subscription',   1
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',   -350.0, '+0 months', '+8 days',  'Spotify Premium',        1
    UNION ALL SELECT 'City Bank Card', 'Subscriptions',  -1200.0, '+0 months', '+9 days',  'Adobe Creative Cloud',   1
    UNION ALL SELECT 'Main Checking',  'Dining',         -1100.0, '+0 months', '+10 days', 'Iftar with friends',     0
    -- Deliberate outlier: roughly 10x the usual Dining spend, for the Stage 6 anomaly demo.
    UNION ALL SELECT 'City Bank Card', 'Dining',        -14500.0, '+0 months', '+12 days', 'Anniversary dinner',     0
    UNION ALL SELECT 'Main Checking',  'Utilities',      -1875.0, '+0 months', '+15 days', 'Electricity bill',       1
) AS v
JOIN accounts   a ON a.name = v.acc
JOIN categories c ON c.name = v.cat;

INSERT INTO budgets (category_id, month, target_amount)
SELECT c.id, strftime('%Y-%m', date('now', 'start of month', v.month_offset)), v.target
FROM (
    SELECT 'Groceries' AS cat, 8000.0 AS target, '+0 months' AS month_offset
    UNION ALL SELECT 'Dining',        5000.0, '+0 months'
    UNION ALL SELECT 'Transport',     2000.0, '+0 months'
    UNION ALL SELECT 'Subscriptions', 1500.0, '+0 months'
    UNION ALL SELECT 'Entertainment', 3000.0, '+0 months'
    UNION ALL SELECT 'Utilities',     2500.0, '+0 months'
    UNION ALL SELECT 'Groceries',     8000.0, '-1 month'
    UNION ALL SELECT 'Dining',        5000.0, '-1 month'
    UNION ALL SELECT 'Subscriptions', 1500.0, '-1 month'
) AS v
JOIN categories c ON c.name = v.cat;

INSERT INTO recurring_rules (description_pattern, category_id, expected_amount, frequency, status)
SELECT v.pattern, c.id, v.amount, v.freq, v.status
FROM (
    SELECT 'netflix' AS pattern, 'Subscriptions' AS cat, -599.0 AS amount, 'monthly' AS freq, 'active' AS status
    UNION ALL SELECT 'spotify',          'Subscriptions',   -350.0, 'monthly', 'active'
    UNION ALL SELECT 'adobe creative',   'Subscriptions',  -1200.0, 'monthly', 'flagged'
    UNION ALL SELECT 'house rent',       'Rent',          -22000.0, 'monthly', 'active'
    UNION ALL SELECT 'electricity bill', 'Utilities',      -1800.0, 'monthly', 'active'
    UNION ALL SELECT 'monthly salary',   'Salary',         65000.0, 'monthly', 'active'
) AS v
JOIN categories c ON c.name = v.cat;
