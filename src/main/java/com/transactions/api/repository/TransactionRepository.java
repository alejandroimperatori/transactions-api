package com.transactions.api.repository;

import com.transactions.api.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    void save(Transaction transaction);
    Optional<Transaction> findById(String id);
    void updateSumAmount(String id, BigDecimal amount, String updatedAt);
    List<String> fetchIdsByType(String type);
    Optional<Transaction> getById(String id);
}
