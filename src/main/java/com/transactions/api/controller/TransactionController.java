package com.transactions.api.controller;

import com.transactions.api.dto.CreateTransactionRequest;
import com.transactions.api.dto.CreateTransactionResponse;
import com.transactions.api.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<CreateTransactionResponse> create(
            @RequestBody @Valid CreateTransactionRequest request) {
        String id = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateTransactionResponse(id));
    }
}
