package com.transactions.api.service;

import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.model.Transaction;
import com.transactions.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionRepository repository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        service = new TransactionService(repository);
    }

    @Test
    void create_successWithParentID() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("150.00"), "payment", "parent-id-1");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        String txnID = service.create(request);

        verify(repository).saveWithOptionalParentUpdate(captor.capture());
        Transaction saved = captor.getValue();


        assertThat(saved.getId()).isEqualTo(captor.getValue().getId());
        assertThat(saved.getType()).isEqualTo("payment");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(saved.getParentId()).isEqualTo("parent-id-1");
        assertThat(saved.getSumAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCreatedAt()).isNotBlank();
        assertThat(saved.getUpdatedAt()).isNotBlank();
    }

    @Test
    void create_successNoParentID() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("50.00"), "payment", null);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        service.create(request);

        verify(repository).saveWithOptionalParentUpdate(captor.capture());
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    void create_propagatesException() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("100.00"), "payment", null);

        doThrow(new RuntimeException("db error")).when(repository).saveWithOptionalParentUpdate(any());

        assertThatThrownBy(() -> service.create(request))
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

        List<String> result = service.fetchIdsByType("unknown");

        assertThat(result).isEmpty();
    }

}
