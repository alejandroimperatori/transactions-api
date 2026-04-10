package com.transactions.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.exception.TransactionNotFoundException;
import com.transactions.api.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    void create_successNoParentID() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("100.00"), "payment", null);

        when(transactionService.create(request)).thenReturn("abc-123");

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction_id").value("abc-123"));
    }

    @Test
    void create_successWithParentID() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("50.00"), "payment", "parent-789");

        when(transactionService.create(request)).thenReturn("child-456");

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction_id").value("child-456"));
    }

    @Test
    void create_badRequestNoAmount() throws Exception {
        String body = "{\"type\":\"payment\"}";

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void create_badRequestNoType() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("100.00"), "", null);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void create_notFoundParentNotFound() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                new BigDecimal("100.00"), "payment", "parent-999");

        when(transactionService.create(request))
                .thenThrow(new TransactionNotFoundException("parent-999"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void fetchIdsByType_success() throws Exception {
        when(transactionService.fetchIdsByType("payment")).thenReturn(List.of("id-1", "id-2", "id-3"));

        mockMvc.perform(get("/transactions/types/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("id-1"))
                .andExpect(jsonPath("$[1]").value("id-2"))
                .andExpect(jsonPath("$[2]").value("id-3"));
    }

    @Test
    void fetchIdsByType_successEmptyList() throws Exception {
        when(transactionService.fetchIdsByType("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/transactions/types/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getSumAmount_success() throws Exception {
        when(transactionService.getSumAmount("tx-1")).thenReturn(new java.math.BigDecimal("250.00"));

        mockMvc.perform(get("/transactions/sum/tx-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(250.00));
    }

    @Test
    void getSumAmount_notFound() throws Exception {
        when(transactionService.getSumAmount("tx-missing"))
                .thenThrow(new TransactionNotFoundException("tx-missing"));

        mockMvc.perform(get("/transactions/sum/tx-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
