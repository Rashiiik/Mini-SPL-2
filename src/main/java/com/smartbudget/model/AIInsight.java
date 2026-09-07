package com.smartbudget.model;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A stored piece of AI-generated text plus what the user did with it.
 *
 * <p>An insight is attached to exactly one subject: either a transaction (an
 * anomaly explanation) or a report month (a monthly narrative). The database
 * enforces that exclusivity; the two static factories are the only intended way
 * to build one, so an invalid combination is hard to construct by accident.
 */
public class AIInsight {

    private Integer id;
    private Integer relatedTransactionId;
    private YearMonth relatedReportMonth;
    private InsightKind kind;
    private String generatedText;
    private UserAction userAction;
    private LocalDateTime createdAt;

    public AIInsight() {
        this.userAction = UserAction.PENDING;
    }

    public static AIInsight forTransaction(int transactionId, InsightKind kind, String text) {
        AIInsight insight = new AIInsight();
        insight.relatedTransactionId = transactionId;
        insight.kind = kind;
        insight.generatedText = text;
        return insight;
    }

    public static AIInsight forReport(YearMonth month, String text) {
        AIInsight insight = new AIInsight();
        insight.relatedReportMonth = month;
        insight.kind = InsightKind.REPORT;
        insight.generatedText = text;
        return insight;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRelatedTransactionId() {
        return relatedTransactionId;
    }

    public void setRelatedTransactionId(Integer relatedTransactionId) {
        this.relatedTransactionId = relatedTransactionId;
    }

    public YearMonth getRelatedReportMonth() {
        return relatedReportMonth;
    }

    public void setRelatedReportMonth(YearMonth relatedReportMonth) {
        this.relatedReportMonth = relatedReportMonth;
    }

    public InsightKind getKind() {
        return kind;
    }

    public void setKind(InsightKind kind) {
        this.kind = kind;
    }

    public String getGeneratedText() {
        return generatedText;
    }

    public void setGeneratedText(String generatedText) {
        this.generatedText = generatedText;
    }

    public UserAction getUserAction() {
        return userAction;
    }

    public void setUserAction(UserAction userAction) {
        this.userAction = userAction;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AIInsight insight)) {
            return false;
        }
        return id != null && id.equals(insight.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return kind + ": " + generatedText;
    }
}
