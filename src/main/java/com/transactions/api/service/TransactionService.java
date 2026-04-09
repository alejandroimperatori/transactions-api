package com.transactions.api.service;

import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public String create(CreateTransactionRequest request) {
        String id  = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        String parentId = request.parentId();

        Transaction transaction = new Transaction(
                id,
                request.type(),
                request.amount(),
                parentId,
                BigDecimal.ZERO,   // sum_amount starts at 0, will be incremented by children transactions
                now,
                now
        );

        repository.saveWithOptionalParentUpdate(transaction);

        return id;
    }
}
