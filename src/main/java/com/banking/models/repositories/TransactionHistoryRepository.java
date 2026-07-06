package com.banking.models.repositories;

import com.banking.models.constant.TransactionStatus;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionHistory t " +
           "WHERE t.fromAccount = :account " +
           "AND t.transactionTime >= :startOfDay " +
           "AND t.transactionTime <= :endOfDay " +
           "AND t.status = :status")
    BigDecimal sumAmountByAccountAndTransactionTimeBetweenAndStatus(
            @Param("account") BankAccount account,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("status") TransactionStatus status);
}
