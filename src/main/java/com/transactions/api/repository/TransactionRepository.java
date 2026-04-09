package com.transactions.api.repository;

import com.transactions.api.model.Transaction;

public interface TransactionRepository {
    void saveWithOptionalParentUpdate(Transaction transaction);
}
