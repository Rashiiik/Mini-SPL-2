# SmartBudget — AI-Assisted Personal Finance Manager

A desktop personal finance application built with **JavaFX**, **Maven**, and **SQLite**, developed as the final project for the Design Patterns course.

The application helps users track accounts, transactions, and budgets while using AI to automatically categorize spending, flag unusual transactions and forgotten subscriptions, and extract transaction data directly from photographed receipts.

---

## Problem Statement

Manually tracking and categorizing personal expenses is tedious and error-prone, which causes many people to lose visibility into where their money actually goes. Existing budgeting apps largely require users to categorize every transaction by hand or rely on rigid, rule-based matching that fails on unfamiliar merchants or unusual spending patterns. As a result, users often miss early warning signs of overspending, fail to notice small recurring subscription charges that quietly accumulate over time, and receive no meaningful explanation when something in their spending looks abnormal.

SmartBudget addresses this by automatically categorizing transactions using AI, flagging unusual spending and redundant subscriptions with a written explanation, and letting users add transactions by simply photographing a receipt. The system is designed to degrade gracefully — if AI classification is ever unavailable, a rule-based fallback keeps the application fully functional.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | JavaFX |
| Build / Dependency Management | Maven |
| Database | SQLite |
| AI Provider | Groq API (OpenAI-compatible endpoint; text + vision models) |
| HTTP Client | Java `HttpClient` (no external SDK) |

---

## Core Features

- **Account management** — checking, savings, and credit card accounts with running balances
- **Transaction tracking** — manual entry, editing, deletion, and AI-assisted categorization
- **Receipt scanning** — upload a photo of a receipt; AI extracts merchant, amount, and date for review before saving
- **Budgeting** — set monthly targets per category, track actuals, receive alerts when a budget is exceeded
- **Subscription-creep detection** — flags recurring charges that look abandoned or redundant
- **Anomaly detection** — flags unusual transactions with a short AI-written explanation of why
- **Reports & analytics** — spending breakdown by category, budget vs. actual, AI-generated monthly insights

---

## Database Schema (Entities)

| Table | Purpose |
|---|---|
| `Accounts` | id, name, type (checking/savings/credit), balance, currency |
| `Transactions` | id, account_id, amount, date, description, category_id, is_recurring |
| `Categories` | id, name, type (expense/income) |
| `Budgets` | id, category_id, month, target_amount |
| `RecurringRules` | id, description_pattern, category_id, expected_amount, frequency |
| `AIInsights` | id, related_transaction_id/report_id, generated_text, accepted_or_edited_by_user, timestamp |

Relationships are enforced with primary/foreign keys and constraints. Seeder scripts populate default categories and sample data on first run.

---

## Multi-Step Workflows

1. **Categorize & Alert** — Add transaction → AI auto-categorizes → user confirms/overrides → budget check → alert if over budget.
2. **Budget Cycle** — Monthly budget setup → allocate per-category targets → system tracks actuals → generates end-of-month report with an AI insight layer.
3. **Subscription Review** — Recurring rule detection → subscription-creep flag → user reviews and confirms, cancels, or ignores.
4. **Receipt Import** — Upload receipt image → AI vision extraction of merchant/amount/date → user reviews and corrects extracted fields → confirmed transaction is categorized and saved.

---

## Design Patterns Used

Full justification for each — the problem, why this pattern, alternatives
rejected, and future benefit — is in [docs/DESIGN.md](docs/DESIGN.md).

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `CategorizationStrategy` — rule-based and AI implementations | The AI strategy was added without modifying `TransactionService`, and holds the rule-based one as its fallback |
| **Adapter** | `AIProvider` / `GroqAdapter` / `NullAIProvider` | Confines Groq's OpenAI-shaped JSON to one class; never throws, so degradation is a one-line `orElseGet` |
| **Observer** | `BudgetEventBus` → `DashboardView` | `BudgetService` does not know a dashboard exists; alerts fire on the crossing, not on every later purchase |
| **Decorator** | `AIInsightReport` wraps `BaseReport` | Optional AI commentary, toggled by a checkbox at runtime; decorators compose |
| **Factory Method** | `AccountFactory` | The three account types differ in genuine initial state — a credit balance is debt owed |
| **Command** | `AddTransactionCommand` + `CommandHistory` | Natural-language entry with a real `undo()` that reverses the balance correctly |

`Database` is the only Singleton, and DAOs receive it by constructor so tests
inject an in-memory instance instead.

---

## Screens

Dashboard · Accounts · Transactions · Budgets · Receipts · Reports ·
Subscriptions · Insights

---

## Running

Requires JDK 21 and Maven.

```bash
mvn javafx:run
```

The database is created and seeded on first launch and kept afterwards.

```bash
mvn test
```

125 tests. None needs a network or a display.

### Enabling AI (optional)

The application is **fully functional without any AI key**. Categorisation falls
back to recurring rules and keywords, anomaly explanations are generated from the
statistics, and reports still produce a written insight. Only receipt scanning
requires AI, and it says so plainly.

To enable it, copy `config.properties.example` to `config.properties` and add a
Groq API key. That file is git-ignored. A `GROQ_API_KEY` environment variable
works too and takes priority.

Model IDs vary by account. List yours with:

```bash
curl https://api.groq.com/openai/v1/models -H "Authorization: Bearer YOUR_KEY"
```

---

## Documentation

| Document | Contents |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Architecture, database design, pattern justifications, testing, known limitations |
| [ER diagram](https://lucid.app/lucidchart/701ef738-8cc7-4856-835a-8a2fdd3db8a8/view) | Six tables with foreign keys and constraints |
| [UML class diagram](https://lucid.app/lucidchart/e36f3c41-6969-4fe1-a161-6d87a67fefdb/view) | Every class, grouped by layer, with the six patterns labelled |
| `docs/test-receipts/` | Sample receipt images for testing receipt import |

---

## Team

- **Member 1:** Jinnia Sultana Prova — Roll: 1627
- **Member 2:** Rashik Raihan — Roll: 1619

