package com.transactions.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
public class DynamoDbTableInitializer implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2_000;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbTableInitializer(DynamoDbClient dynamoDbClient,
                                    @Value("${aws.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ensureTableExists();
                return;
            } catch (Exception e) {
                log.warn("DynamoDB table init attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS);
                } else {
                    throw new IllegalStateException("Could not initialize DynamoDB table after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
    }

    private void ensureTableExists() {
        try {
            dynamoDbClient.describeTable(r -> r.tableName(tableName));
            log.info("DynamoDB table '{}' already exists", tableName);
        } catch (ResourceNotFoundException e) {
            createTable();
        }
    }

    private void createTable() {
        dynamoDbClient.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("type").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(
                        KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()
                )
                .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                                .indexName("type-index")
                                .keySchema(KeySchemaElement.builder().attributeName("type").keyType(KeyType.HASH).build())
                                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                .build()
                )
                .build());

        log.info("DynamoDB table '{}' created with GSI 'type-index'", tableName);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
