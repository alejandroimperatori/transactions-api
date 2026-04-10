package com.transactions.api.repository;

import com.transactions.api.exception.ParentTransactionNotFoundException;
import com.transactions.api.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoDbTransactionRepositoryTest {

    private static final String TABLE_NAME = "test-transactions";

    private DynamoDbClient dynamoDbClient;
    private DynamoDbTransactionRepository repository;

    @BeforeEach
    void setUp() {
        dynamoDbClient = mock(DynamoDbClient.class);
        repository = new DynamoDbTransactionRepository(dynamoDbClient, TABLE_NAME);
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());
    }

    private Transaction buildTransaction(String id, String type, BigDecimal amount, String parentId) {
        return new Transaction(id, type, amount, parentId, BigDecimal.ZERO,
                "2026-04-09T00:00:00Z", "2026-04-00T00:00:00Z");
    }

    private Map<String, AttributeValue> idItem(String id) {
        return Map.of("id", AttributeValue.builder().s(id).build());
    }

    @Test
    void fetchIdsByType_returnsEmptyListWhenNoItems() {
        QueryResponse response = QueryResponse.builder()
                .items(List.of())
                .lastEvaluatedKey(Map.of())
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        List<String> ids = repository.fetchIdsByType("unknown");

        assertThat(ids).isEmpty();
    }

    @Test
    void saveWithOptionalParentUpdate_successNoParent() {
        Transaction tx = buildTransaction("tx-1", "payment", new BigDecimal("100.00"), null);

        repository.saveWithOptionalParentUpdate(tx);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().put()).isNotNull();
        assertThat(items.getFirst().update()).isNull();
        assertThat(items.getFirst().put().item().get("id").s()).isEqualTo("tx-1");
        assertThat(items.getFirst().put().item().get("type").s()).isEqualTo("payment");
        assertThat(items.getFirst().put().item().get("amount").n()).isEqualTo("100.00");
        assertThat(items.getFirst().put().item()).doesNotContainKey("parent_id");
    }

    @Test
    void saveWithOptionalParentUpdate_successWithParent() {
        Transaction tx = buildTransaction("tx-2", "payment", new BigDecimal("100.00"), "parent-tx");

        repository.saveWithOptionalParentUpdate(tx);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().put()).isNotNull();
        assertThat(items.getFirst().put().item().get("id").s()).isEqualTo("tx-2");
        assertThat(items.getFirst().put().item().get("type").s()).isEqualTo("payment");
        assertThat(items.getFirst().put().item().get("amount").n()).isEqualTo("100.00");
        assertThat(items.getFirst().put().item().get("parent_id").s()).isEqualTo("parent-tx");
        assertThat(items.get(1).update()).isNotNull();
    }

    @Test
    void saveWithOptionalParentUpdate_parentNotFoundException() {
        Transaction tx = buildTransaction("tx-6", "payment", new BigDecimal("10.00"), "missing-parent");

        CancellationReason putOk = CancellationReason.builder().code("None").build();
        CancellationReason parentFailed = CancellationReason.builder().code("ConditionalCheckFailed").build();

        TransactionCanceledException exception = TransactionCanceledException.builder()
                .cancellationReasons(List.of(putOk, parentFailed))
                .build();

        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> repository.saveWithOptionalParentUpdate(tx))
                .isInstanceOf(ParentTransactionNotFoundException.class)
                .hasMessageContaining("missing-parent");
    }

    @Test
    void fetchIdsByType_singlePage() {
        QueryResponse response = QueryResponse.builder()
                .items(idItem("tx-a"), idItem("tx-b"))
                .lastEvaluatedKey(Map.of())
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        List<String> ids = repository.fetchIdsByType("payment");

        assertThat(ids).containsExactly("tx-a", "tx-b");
        verify(dynamoDbClient, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void fetchIdsByType_multiplePages() {
        Map<String, AttributeValue> lastKey = Map.of(
                "id", AttributeValue.builder().s("tx-b").build(),
                "type", AttributeValue.builder().s("payment").build()
        );

        QueryResponse firstPage = QueryResponse.builder()
                .items(idItem("tx-a"), idItem("tx-b"))
                .lastEvaluatedKey(lastKey)
                .build();

        QueryResponse secondPage = QueryResponse.builder()
                .items(idItem("tx-c"))
                .lastEvaluatedKey(Map.of())
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        List<String> ids = repository.fetchIdsByType("payment");

        assertThat(ids).containsExactly("tx-a", "tx-b", "tx-c");
        verify(dynamoDbClient, times(2)).query(any(QueryRequest.class));
    }

}
