package com.banking.controllers;

import com.banking.models.entities.BankAccount;
import com.banking.models.repositories.BankAccountRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bankAccounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountRepository bankAccountRepository;

    @GetMapping
    public ResponseEntity<com.banking.advice.ApiResponse<List<BankAccount>>> getAllBankAccounts() {
        return ResponseEntity.ok(com.banking.advice.ApiResponse.success(bankAccountRepository.findAll(),
                "Fetched all bank account successfully"));
    }
}
