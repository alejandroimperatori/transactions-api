package com.transactions.api.repository;

import com.transactions.api.exception.ParentTransactionNotFoundException;
import com.transactions.api.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

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
    public void saveWithOptionalParentUpdate(Transaction transaction) {
        List<TransactWriteItem> items = new ArrayList<>();

        Map<String, AttributeValue> item = buildItem(transaction);

        items.add(TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression("attribute_not_exists(id)")
                        .build())
                .build());

        if (transaction.getParentId() != null) {
            Map<String, AttributeValue> parentKey = Map.of(
                    "id", AttributeValue.builder().s(transaction.getParentId()).build()
            );

            Map<String, AttributeValue> exprValues = Map.of(
                    ":amount",    AttributeValue.builder().n(transaction.getAmount().toPlainString()).build(),
                    ":updatedAt", AttributeValue.builder().s(transaction.getUpdatedAt()).build()
            );

            items.add(TransactWriteItem.builder()
                    .update(Update.builder()
                            .tableName(tableName)
                            .key(parentKey)
                            .updateExpression("ADD sum_amount :amount SET updated_at = :updatedAt")
                            .conditionExpression("attribute_exists(id)")
                            .expressionAttributeValues(exprValues)
                            .build())
                    .build());
        }

        try {
            dynamoDbClient.transactWriteItems(
                    TransactWriteItemsRequest.builder().transactItems(items).build()
            );
        } catch (TransactionCanceledException e) {
            List<CancellationReason> reasons = e.cancellationReasons();
            if (reasons.size() > 1 && "ConditionalCheckFailed".equals(reasons.get(1).code())) {
                throw new ParentTransactionNotFoundException(transaction.getParentId());
            }

            throw e;
        }
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
