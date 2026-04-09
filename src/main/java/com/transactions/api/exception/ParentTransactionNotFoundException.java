package com.transactions.api.exception;

public class ParentTransactionNotFoundException extends RuntimeException {

    public ParentTransactionNotFoundException(String parentId) {
        super("Parent transaction not found: " + parentId);
    }
}
