package com.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull @Positive
        @JsonProperty("amount") BigDecimal amount,

        @NotBlank
        @JsonProperty("type") String type,

        @JsonProperty("parent_id") String parentId
) {
}
