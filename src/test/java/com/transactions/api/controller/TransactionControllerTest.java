package com.transactions.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.exception.ParentTransactionNotFoundException;
import com.transactions.api.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
                .thenThrow(new ParentTransactionNotFoundException("parent-999"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
