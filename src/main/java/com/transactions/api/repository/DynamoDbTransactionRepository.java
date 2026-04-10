package com.transactions.api.repository;

import com.transactions.api.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbTransactionRepository implements TransactionRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbTransactionRepository(DynamoDbClient dynamoDbClient,
                                         @Value("${aws.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(Transaction transaction) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(buildItem(transaction))
                .conditionExpression("attribute_not_exists(id)")
                .build());
    }

    @Override
    public Optional<Transaction> findById(String id) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(id).build()))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapItem(response.item()));
    }

    @Override
    public void updateSumAmount(String id, BigDecimal amount, String updatedAt) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(id).build()))
                .updateExpression("ADD sum_amount :amount SET updated_at = :updatedAt")
                .expressionAttributeValues(Map.of(
                        ":amount",    AttributeValue.builder().n(amount.toPlainString()).build(),
                        ":updatedAt", AttributeValue.builder().s(updatedAt).build()
                ))
                .build());
    }

    @Override
    public List<String> fetchIdsByType(String type) {
        List<String> ids = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            QueryRequest.Builder requestBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("type-index")
                    .keyConditionExpression("#t = :type")
                    .expressionAttributeNames(Map.of("#t", "type"))
                    .expressionAttributeValues(Map.of(":type", AttributeValue.builder().s(type).build()))
                    .projectionExpression("id");

            if (lastKey != null) {
                requestBuilder.exclusiveStartKey(lastKey);
            }

            QueryResponse response = dynamoDbClient.query(requestBuilder.build());
            response.items().forEach(item -> ids.add(item.get("id").s()));
            lastKey = response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey();
        } while (lastKey != null);

        return ids;
    }

    @Override
    public Optional<Transaction> getById(String id) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(id).build()))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapItem(response.item()));
    }

    private Transaction mapItem(Map<String, AttributeValue> item) {
        Transaction transaction = new Transaction();
        transaction.setId(item.get("id").s());
        transaction.setType(item.get("type").s());
        transaction.setAmount(new BigDecimal(item.get("amount").n()));
        transaction.setSumAmount(new BigDecimal(item.get("sum_amount").n())
                .setScale(2, RoundingMode.HALF_UP));
        transaction.setCreatedAt(item.get("created_at").s());
        transaction.setUpdatedAt(item.get("updated_at").s());
        if (item.containsKey("parent_id")) {
            transaction.setParentId(item.get("parent_id").s());
        }
        return transaction;
    }

    private Map<String, AttributeValue> buildItem(Transaction t) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id",         AttributeValue.builder().s(t.getId()).build());
        item.put("type",       AttributeValue.builder().s(t.getType()).build());
        item.put("amount",     AttributeValue.builder().n(t.getAmount().toPlainString()).build());
        item.put("sum_amount", AttributeValue.builder().n(t.getSumAmount().toPlainString()).build());
        item.put("created_at", AttributeValue.builder().s(t.getCreatedAt()).build());
        item.put("updated_at", AttributeValue.builder().s(t.getUpdatedAt()).build());
        if (t.getParentId() != null) {
            item.put("parent_id", AttributeValue.builder().s(t.getParentId()).build());
        }
        return item;
    }
}
