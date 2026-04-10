package com.transactions.api.service;

import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.exception.TransactionNotFoundException;
import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final AncestorSumAmountUpdater ancestorSumAmountUpdater;

    public TransactionService(TransactionRepository repository,
                              AncestorSumAmountUpdater ancestorSumAmountUpdater) {
        this.repository = repository;
        this.ancestorSumAmountUpdater = ancestorSumAmountUpdater;
    }

    public String create(CreateTransactionRequest request) {
        String id  = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String parentId = request.parentId();

        if (parentId != null) {
            repository.findById(parentId)
                    .orElseThrow(() -> new TransactionNotFoundException(parentId));
        }

        Transaction transaction = new Transaction(
                id,
                request.type(),
                request.amount(),
                parentId,
                request.amount(),
                now,
                now
        );

        repository.save(transaction);

        if (parentId != null) {
            ancestorSumAmountUpdater.updateAncestors(parentId, request.amount(), now);
        }

        return id;
    }

    public List<String> fetchIdsByType(String type) {
        return repository.fetchIdsByType(type);
    }

    public BigDecimal getSumAmount(String id) {
        Transaction transaction = repository.getById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        return transaction.getSumAmount();
    }
}
