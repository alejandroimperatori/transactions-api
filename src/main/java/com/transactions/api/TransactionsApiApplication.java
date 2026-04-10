package com.transactions.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TransactionsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionsApiApplication.class, args);
    }
}