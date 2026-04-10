package com.transactions.api.service;

import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AncestorSumAmountUpdaterTest {

    private TransactionRepository repository;
    private AncestorSumAmountUpdater updater;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        updater = new AncestorSumAmountUpdater(repository);
    }

    private Transaction tx(String id, String parentId) {
        return new Transaction(id, "payment", new BigDecimal("100.00"), parentId,
                new BigDecimal("100.00"), "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }

    @Test
    void updateAncestors_incrementsDirectParent() {
        when(repository.findById("parent-1")).thenReturn(Optional.of(tx("parent-1", null)));

        updater.updateAncestors("parent-1", new BigDecimal("50.00"), "2026-04-09T00:00:00Z");

        verify(repository).updateSumAmount("parent-1", new BigDecimal("50.00"), "2026-04-09T00:00:00Z");
    }

    @Test
    void updateAncestors_incrementsAllAncestors() {
        when(repository.findById("child-1")).thenReturn(Optional.of(tx("child-1", "root-1")));
        when(repository.findById("root-1")).thenReturn(Optional.of(tx("root-1", null)));

        updater.updateAncestors("child-1", new BigDecimal("30.00"), "2026-04-09T00:00:00Z");

        verify(repository).updateSumAmount(eq("child-1"), any(), any());
        verify(repository).updateSumAmount(eq("root-1"), any(), any());
        verify(repository, times(2)).updateSumAmount(any(), any(), any());
    }

    @Test
    void updateAncestors_stopsGracefullyWhenAncestorNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        updater.updateAncestors("missing", new BigDecimal("10.00"), "2026-04-09T00:00:00Z");

        verify(repository, never()).updateSumAmount(any(), any(), any());
    }

}
