package com.transactions.api.service;

import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AncestorSumAmountUpdater {

    private final TransactionRepository repository;

    public AncestorSumAmountUpdater(TransactionRepository repository) {
        this.repository = repository;
    }

    @Async
    public void updateAncestors(String parentId, BigDecimal amount, String updatedAt) {
        String currentId = parentId;
        while (currentId != null) {
            Optional<Transaction> ancestor = repository.findById(currentId);
            if (ancestor.isEmpty()) {
                break;
            }
            repository.updateSumAmount(currentId, amount, updatedAt);
            currentId = ancestor.get().getParentId();
        }
    }
}
