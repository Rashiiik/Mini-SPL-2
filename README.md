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

## Probable Design Patterns

These patterns were identified because they solve a real structural problem in this application, not to satisfy a checklist. Final selection may be refined as implementation progresses.

| Pattern | Where it's used | Why |
|---|---|---|
| **Strategy** | `CategorizationStrategy` (AI vs. rule-based fallback); `TransactionSourceStrategy` (manual entry vs. receipt image extraction) | Swappable algorithms behind one interface; app degrades gracefully if AI is unavailable |
| **Observer** | Budget threshold alerts | Notifies dashboard/UI components when spending exceeds budget, without coupling transaction logic to specific listeners |
| **Decorator** | Report generation | Wraps a base report with an AI-insight layer without modifying the base report class |
| **Adapter** | AI provider integration (Groq text + vision models) | Isolates external API calls so switching providers later only touches the adapter |
| **Factory Method** *(secondary)* | Account creation | Encapsulates type-specific defaults (checking, savings, credit card) |
| **Command** *(secondary, if implemented)* | Natural-language transaction entry | Parses free-text input into a validated, executable action object |

---

## Team

- **Member 1:** Jinnia Sultana Prova — Roll: 1627
- **Member 2:** Rashik Raihan — Roll: 1619

