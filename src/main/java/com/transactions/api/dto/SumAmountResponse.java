package com.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SumAmountResponse(
        @JsonProperty("sum") BigDecimal sumAmount
) {}
