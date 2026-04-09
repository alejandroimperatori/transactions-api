package com.transactions.api.model;

import java.math.BigDecimal;

public class Transaction {

    private String id;
    private String type;
    private BigDecimal amount;
    private String parentId;
    private BigDecimal sumAmount;
    private String createdAt;
    private String updatedAt;

    public Transaction() {}

    public Transaction(String id, String type, BigDecimal amount, String parentId,
                       BigDecimal sumAmount, String createdAt, String updatedAt) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.parentId = parentId;
        this.sumAmount = sumAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public BigDecimal getSumAmount() { return sumAmount; }
    public void setSumAmount(BigDecimal sumAmount) { this.sumAmount = sumAmount; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
