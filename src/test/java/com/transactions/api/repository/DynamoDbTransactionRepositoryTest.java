package com.transactions.api.repository;

import com.transactions.api.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());
    }

    private Transaction buildTransaction(String id, String type, BigDecimal amount, String parentId) {
        return new Transaction(id, type, amount, parentId, amount,
                "2026-04-09T00:00:00Z", "2026-04-09T00:00:00Z");
    }

    private Map<String, AttributeValue> idItem(String id) {
        return Map.of("id", AttributeValue.builder().s(id).build());
    }

    // --- save ---

    @Test
    void save_persistsItemWithCorrectAttributes() {
        Transaction tx = buildTransaction("tx-1", "payment", new BigDecimal("100.00"), null);

        repository.save(tx);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        Map<String, AttributeValue> item = captor.getValue().item();
        assertThat(captor.getValue().tableName()).isEqualTo(TABLE_NAME);
        assertThat(item.get("id").s()).isEqualTo("tx-1");
        assertThat(item.get("type").s()).isEqualTo("payment");
        assertThat(item.get("amount").n()).isEqualTo("100.00");
        assertThat(item).doesNotContainKey("parent_id");
    }

    @Test
    void save_includesParentIdWhenPresent() {
        Transaction tx = buildTransaction("tx-2", "payment", new BigDecimal("50.00"), "parent-1");

        repository.save(tx);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        assertThat(captor.getValue().item().get("parent_id").s()).isEqualTo("parent-1");
    }

    // --- findById ---

    @Test
    void findById_returnsTransactionWhenFound() {
        Map<String, AttributeValue> item = Map.of(
                "id",         AttributeValue.builder().s("tx-9").build(),
                "type",       AttributeValue.builder().s("payment").build(),
                "amount",     AttributeValue.builder().n("100.00").build(),
                "sum_amount", AttributeValue.builder().n("250.00").build(),
                "created_at", AttributeValue.builder().s("2026-01-01T00:00:00Z").build(),
                "updated_at", AttributeValue.builder().s("2026-01-01T00:00:00Z").build()
        );
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(item).build());

        Optional<Transaction> result = repository.findById("tx-9");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("tx-9");
        assertThat(result.get().getSumAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(result.get().getParentId()).isNull();
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        assertThat(repository.findById("missing")).isEmpty();
    }

    // --- incrementSumAmount ---

    @Test
    void updateSumAmount_sendsCorrectUpdateExpression() {
        repository.updateSumAmount("tx-1", new BigDecimal("50.00"), "2026-04-09T00:00:00Z");

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest req = captor.getValue();
        assertThat(req.tableName()).isEqualTo(TABLE_NAME);
        assertThat(req.key().get("id").s()).isEqualTo("tx-1");
        assertThat(req.updateExpression()).contains("ADD sum_amount :amount");
        assertThat(req.expressionAttributeValues().get(":amount").n()).isEqualTo("50.00");
    }

    // --- fetchIdsByType ---

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
    void fetchIdsByType_singlePage() {
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(idItem("tx-a"), idItem("tx-b")).lastEvaluatedKey(Map.of()).build());

        assertThat(repository.fetchIdsByType("payment")).containsExactly("tx-a", "tx-b");
        verify(dynamoDbClient, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void fetchIdsByType_multiplePages() {
        Map<String, AttributeValue> lastKey = Map.of(
                "id", AttributeValue.builder().s("tx-b").build(),
                "type", AttributeValue.builder().s("payment").build()
        );

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(idItem("tx-a"), idItem("tx-b")).lastEvaluatedKey(lastKey).build())
                .thenReturn(QueryResponse.builder().items(idItem("tx-c")).lastEvaluatedKey(Map.of()).build());

        assertThat(repository.fetchIdsByType("payment")).containsExactly("tx-a", "tx-b", "tx-c");
        verify(dynamoDbClient, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void getById_returnsTransactionWhenFound() {
        Map<String, AttributeValue> item = Map.of(
                "id",         AttributeValue.builder().s("tx-9").build(),
                "type",       AttributeValue.builder().s("payment").build(),
                "amount",     AttributeValue.builder().n("100.00").build(),
                "sum_amount", AttributeValue.builder().n("250.00").build(),
                "created_at", AttributeValue.builder().s("2026-01-01T00:00:00Z").build(),
                "updated_at", AttributeValue.builder().s("2026-01-01T00:00:00Z").build()
        );
        GetItemResponse response = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        java.util.Optional<com.transactions.api.model.Transaction> result = repository.getById("tx-9");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("tx-9");
        assertThat(result.get().getType()).isEqualTo("payment");
        assertThat(result.get().getSumAmount()).isEqualByComparingTo(new java.math.BigDecimal("250.00"));
    }

    @Test
    void getById_returnsEmptyWhenNotFound() {
        GetItemResponse response = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        java.util.Optional<com.transactions.api.model.Transaction> result = repository.getById("tx-missing");

        assertThat(result).isEmpty();
    }

}
