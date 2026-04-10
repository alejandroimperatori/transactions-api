package com.transactions.api.service;

import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.exception.TransactionNotFoundException;
import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionRepository repository;
    private AncestorSumAmountUpdater ancestorSumAmountUpdater;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        ancestorSumAmountUpdater = mock(AncestorSumAmountUpdater.class);
        service = new TransactionService(repository, ancestorSumAmountUpdater);
    }

    @Test
    void create_successNoParentID() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("50.00"), "payment", null);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        service.create(request);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isNull();
        assertThat(captor.getValue().getSumAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(ancestorSumAmountUpdater, never()).updateAncestors(any(), any(), any());
    }

    @Test
    void create_successWithParentID() {
        Transaction parent = new Transaction("parent-id-1", "payment", new BigDecimal("200.00"),
                null, new BigDecimal("200.00"), "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
        when(repository.findById("parent-id-1")).thenReturn(Optional.of(parent));

        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("150.00"), "payment", "parent-id-1");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        String txnID = service.create(request);

        verify(repository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(txnID);
        assertThat(saved.getType()).isEqualTo("payment");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(saved.getParentId()).isEqualTo("parent-id-1");
        assertThat(saved.getSumAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(saved.getCreatedAt()).isNotBlank();
        assertThat(saved.getUpdatedAt()).isNotBlank();
    }

    @Test
    void create_triggersAsyncUpdateWhenParentPresent() {
        when(repository.findById("parent-1")).thenReturn(Optional.of(
                new Transaction("parent-1", "payment", new BigDecimal("100.00"),
                        null, new BigDecimal("100.00"), "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")));

        service.create(new CreateTransactionRequest(new BigDecimal("40.00"), "payment", "parent-1"));

        verify(ancestorSumAmountUpdater).updateAncestors(eq("parent-1"), eq(new BigDecimal("40.00")), any());
    }

    @Test
    void create_parentNotFound_throwsAndDoesNotSave() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateTransactionRequest(new BigDecimal("10.00"), "payment", "missing")))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("missing");

        verify(repository, never()).save(any());
        verify(ancestorSumAmountUpdater, never()).updateAncestors(any(), any(), any());
    }

    @Test
    void create_propagatesException() {
        doThrow(new RuntimeException("db error")).when(repository).save(any());

        assertThatThrownBy(() -> service.create(
                new CreateTransactionRequest(new BigDecimal("100.00"), "payment", null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db error");
    }

    @Test
    void fetchIdsByType_returnsIdsFromRepository() {
        when(repository.fetchIdsByType("payment")).thenReturn(List.of("id-1", "id-2"));

        List<String> result = service.fetchIdsByType("payment");

        assertThat(result).containsExactly("id-1", "id-2");
        verify(repository).fetchIdsByType("payment");
    }

    @Test
    void fetchIdsByType_returnsEmptyListWhenNoneFound() {
        when(repository.fetchIdsByType("unknown")).thenReturn(List.of());

        assertThat(service.fetchIdsByType("unknown")).isEmpty();
    }

    @Test
    void getSumAmount_returnsValueFromRepository() {
        Transaction transaction = new Transaction("tx-1", "payment", new BigDecimal("100.00"),
                null, new BigDecimal("350.00"), "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
        when(repository.getById("tx-1")).thenReturn(java.util.Optional.of(transaction));

        BigDecimal result = service.getSumAmount("tx-1");

        assertThat(result).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void getSumAmount_throwsWhenTransactionNotFound() {
        when(repository.getById("tx-missing")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getSumAmount("tx-missing"))
                .isInstanceOf(com.transactions.api.exception.TransactionNotFoundException.class)
                .hasMessageContaining("tx-missing");
    }

}
