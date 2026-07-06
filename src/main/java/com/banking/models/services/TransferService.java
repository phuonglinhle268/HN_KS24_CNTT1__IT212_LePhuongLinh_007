package com.banking.models.services;

import com.banking.exceptions.*;
import com.banking.models.constant.TransactionStatus;
import com.banking.models.dto.TransferRequest;
import com.banking.models.dto.TransferResponse;
import com.banking.models.dto.UpdateDailyLimitRequest;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.TransactionHistory;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        // 1. Validate parameters
        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new ValidationException("Tài khoản nguồn và tài khoản nhận không được để trống.");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Số tiền chuyển phải lớn hơn 0.");
        }

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new ValidationException("Tài khoản chuyển và tài khoản nhận không được giống nhau.");
        }

        // 2. Retrieve accounts with lock ordering to prevent deadlocks
        BankAccount fromAccount;
        BankAccount toAccount;

        if (request.getFromAccountId().compareTo(request.getToAccountId()) < 0) {
            fromAccount = bankAccountRepository.findByIdForUpdate(request.getFromAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản nguồn với ID: " + request.getFromAccountId()));
            toAccount = bankAccountRepository.findByIdForUpdate(request.getToAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản nhận với ID: " + request.getToAccountId()));
        } else {
            toAccount = bankAccountRepository.findByIdForUpdate(request.getToAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản nhận với ID: " + request.getToAccountId()));
            fromAccount = bankAccountRepository.findByIdForUpdate(request.getFromAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản nguồn với ID: " + request.getFromAccountId()));
        }

        // Check account statuses
        if (fromAccount.getStatus() != BankAccount.AccountStatus.ACTIVE) {
            throw new ValidationException("Tài khoản nguồn không ở trạng thái hoạt động.");
        }
        if (toAccount.getStatus() != BankAccount.AccountStatus.ACTIVE) {
            throw new ValidationException("Tài khoản nhận không ở trạng thái hoạt động.");
        }

        // 3. Check Daily Limit
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        BigDecimal todayAmount = transactionHistoryRepository.sumAmountByAccountAndTransactionTimeBetweenAndStatus(
                fromAccount, startOfDay, endOfDay, TransactionStatus.SUCCESS);

        BigDecimal totalWithNewTransaction = todayAmount.add(request.getAmount());
        if (totalWithNewTransaction.compareTo(fromAccount.getDailyLimit()) > 0) {
            // Save Failed Transaction History
            TransactionHistory failedTx = TransactionHistory.builder()
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(request.getAmount())
                    .status(TransactionStatus.FAILED)
                    .transactionTime(LocalDateTime.now())
                    .description("Thất bại: Vượt hạn mức chuyển tiền hàng ngày.")
                    .build();
            transactionHistoryRepository.save(failedTx);

            throw new DailyLimitExceededException("Quý khách đã vượt hạn mức giao dịch trong ngày.");
        }

        // 4. Check Balance
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            // Save Failed Transaction History
            TransactionHistory failedTx = TransactionHistory.builder()
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(request.getAmount())
                    .status(TransactionStatus.FAILED)
                    .transactionTime(LocalDateTime.now())
                    .description("Thất bại: Số dư không đủ.")
                    .build();
            transactionHistoryRepository.save(failedTx);

            throw new InsufficientBalanceException("Số dư không đủ để thực hiện giao dịch.");
        }

        // 5. Update balances and save
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        bankAccountRepository.save(fromAccount);
        bankAccountRepository.save(toAccount);

        // 6. Save Successful Transaction History
        TransactionHistory successTx = TransactionHistory.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .transactionTime(LocalDateTime.now())
                .description("Chuyển tiền thành công.")
                .build();
        successTx = transactionHistoryRepository.save(successTx);

        return TransferResponse.builder()
                .transactionId(successTx.getId())
                .fromAccountId(fromAccount.getId())
                .toAccountId(toAccount.getId())
                .amount(request.getAmount())
                .transactionTime(successTx.getTransactionTime())
                .status(TransactionStatus.SUCCESS)
                .message("Chuyển tiền thành công.")
                .build();
    }

    @Transactional
    public BankAccount updateDailyLimit(Long accountId, UpdateDailyLimitRequest request) {
        if (request.getDailyLimit() == null || request.getDailyLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Hạn mức hàng ngày không được nhỏ hơn 0.");
        }

        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Không tìm thấy tài khoản với ID: " + accountId));

        bankAccount.setDailyLimit(request.getDailyLimit());
        return bankAccountRepository.save(bankAccount);
    }
}
