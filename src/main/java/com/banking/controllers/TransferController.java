package com.banking.controllers;

import com.banking.advice.ApiResponse;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.TransferResponse;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.models.entities.BankAccount;
import com.banking.models.services.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/api/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.transfer(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giao dịch thành công."));
    }

    @PutMapping("/api/accounts/{id}/daily-limit")
    public ResponseEntity<ApiResponse<BankAccount>> updateDailyLimit(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDailyLimitRequest request) {
        BankAccount updatedAccount = transferService.updateDailyLimit(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedAccount, "Cập nhật hạn mức giao dịch thành công."));
    }
}
