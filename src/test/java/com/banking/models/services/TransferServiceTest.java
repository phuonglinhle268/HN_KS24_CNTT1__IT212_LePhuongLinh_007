package com.banking.models.services;

import com.banking.exceptions.DailyLimitExceededException;
import com.banking.exceptions.InsufficientBalanceException;
import com.banking.exceptions.ValidationException;
import com.banking.models.constant.TransactionStatus;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.TransferResponse;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.TransactionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @InjectMocks
    private TransferService transferService;

    private BankAccount fromAccount;
    private BankAccount toAccount;

    @BeforeEach
    void setUp() {
        fromAccount = BankAccount.builder()
                .id(1L)
                .accountNumber("111111")
                .balance(new BigDecimal("100000000.0000")) // 100M
                .dailyLimit(new BigDecimal("50000000.0000")) // 50M
                .status(BankAccount.AccountStatus.ACTIVE)
                .build();

        toAccount = BankAccount.builder()
                .id(2L)
                .accountNumber("222222")
                .balance(new BigDecimal("10000000.0000")) // 10M
                .status(BankAccount.AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void transfer_Success() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("10000000.0000")); // 10M

        when(bankAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(bankAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));
        when(transactionHistoryRepository.sumAmountByAccountAndTransactionTimeBetweenAndStatus(
                eq(fromAccount), any(), any(), eq(TransactionStatus.SUCCESS)))
                .thenReturn(new BigDecimal("0.0000"));

        when(transactionHistoryRepository.save(any(TransactionHistory.class)))
                .thenAnswer(invocation -> {
                    TransactionHistory tx = invocation.getArgument(0);
                    tx.setId(100L);
                    return tx;
                });

        TransferResponse response = transferService.transfer(request);

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("90000000.0000"), fromAccount.getBalance());
        assertEquals(new BigDecimal("20000000.0000"), toAccount.getBalance());
        verify(bankAccountRepository, times(1)).save(fromAccount);
        verify(bankAccountRepository, times(1)).save(toAccount);
    }

    @Test
    void transfer_Fail_DailyLimitExceeded() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("30000000.0000")); // 30M

        when(bankAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(bankAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));
        // Mock sum already transfered today to 25M (25M + 30M > 50M Daily limit)
        when(transactionHistoryRepository.sumAmountByAccountAndTransactionTimeBetweenAndStatus(
                eq(fromAccount), any(), any(), eq(TransactionStatus.SUCCESS)))
                .thenReturn(new BigDecimal("25000000.0000"));

        assertThrows(DailyLimitExceededException.class, () -> transferService.transfer(request));

        // Verify failed transaction is recorded
        verify(transactionHistoryRepository, times(1)).save(argThat(tx -> tx.getStatus() == TransactionStatus.FAILED));
        // Verify balance did not change
        assertEquals(new BigDecimal("100000000.0000"), fromAccount.getBalance());
    }

    @Test
    void transfer_Fail_InsufficientBalance() {
        fromAccount.setDailyLimit(new BigDecimal("300000000.0000")); // 300M
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("200000000.0000")); // 200M

        when(bankAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(bankAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));
        when(transactionHistoryRepository.sumAmountByAccountAndTransactionTimeBetweenAndStatus(
                eq(fromAccount), any(), any(), eq(TransactionStatus.SUCCESS)))
                .thenReturn(new BigDecimal("0.0000"));

        assertThrows(InsufficientBalanceException.class, () -> transferService.transfer(request));

        // Verify failed transaction is recorded
        verify(transactionHistoryRepository, times(1)).save(argThat(tx -> tx.getStatus() == TransactionStatus.FAILED));
        // Verify balance did not change
        assertEquals(new BigDecimal("100000000.0000"), fromAccount.getBalance());
    }

    @Test
    void transfer_Fail_ValidationSameAccount() {
        TransferRequest request = new TransferRequest(1L, 1L, new BigDecimal("10000000.0000"));

        assertThrows(ValidationException.class, () -> transferService.transfer(request));
    }

    @Test
    void updateDailyLimit_Success() {
        UpdateDailyLimitRequest request = new UpdateDailyLimitRequest(new BigDecimal("150000000.0000"));

        when(bankAccountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(bankAccountRepository.save(fromAccount)).thenReturn(fromAccount);

        BankAccount result = transferService.updateDailyLimit(1L, request);

        assertNotNull(result);
        assertEquals(new BigDecimal("150000000.0000"), result.getDailyLimit());
        verify(bankAccountRepository, times(1)).save(fromAccount);
    }
}
