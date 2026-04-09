package com.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransactionResponse(
        @JsonProperty("transaction_id") String transactionId
) {}
