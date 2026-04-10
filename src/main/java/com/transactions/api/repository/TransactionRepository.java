package com.transactions.api.repository;

import com.transactions.api.model.Transaction;

import java.util.List;

public interface TransactionRepository {
    void saveWithOptionalParentUpdate(Transaction transaction);
    List<String> fetchIdsByType(String type);
}