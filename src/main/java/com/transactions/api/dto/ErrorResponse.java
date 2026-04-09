package com.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message
) {}
