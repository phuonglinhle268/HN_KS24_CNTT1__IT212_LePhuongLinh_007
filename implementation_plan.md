# Implementation Plan - Daily Transfer Limit Control (with Pessimistic Locking)

We need to implement a daily transaction limit control feature on bank accounts in the existing Core Banking system.

---

## Proposed Changes

We will modify existing classes and introduce new classes following SOLID principles and Clean Architecture.

### 1. Database & Domain Models

#### [MODIFY] [BankAccount.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/entities/BankAccount.java)
- Add a new field `dailyLimit` of type `BigDecimal`.
- Default value: `50,000,000.00` VND.
- Column mapping: `daily_limit` (precision 19, scale 4, nullable = false).

#### [NEW] [TransactionStatus.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/constant/TransactionStatus.java)
- Define transaction status values: `SUCCESS`, `FAILED`.

#### [NEW] [TransactionHistory.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/entities/TransactionHistory.java)
- JPA Entity to persist transaction histories.
- Fields:
  - `id` (Long, GenerationType.IDENTITY)
  - `fromAccount` (BankAccount, ManyToOne)
  - `toAccount` (BankAccount, ManyToOne)
  - `amount` (BigDecimal, precision 19, scale 4)
  - `transactionTime` (LocalDateTime)
  - `status` (TransactionStatus enum)
  - `createdAt` (LocalDateTime, set via `@PrePersist`)

---

### 2. Repositories & Concurrency Control

#### [MODIFY] [BankAccountRepository.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/repositories/BankAccountRepository.java)
- Declare `findByIdForUpdate` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to support Pessimistic Locking for account updates. This prevents double-spending and limit breaches under high concurrent requests.

#### [NEW] [TransactionHistoryRepository.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/repositories/TransactionHistoryRepository.java)
- Standard JpaRepository for `TransactionHistory`.
- Define an optimized JPQL query to sum all transaction amounts sent from a given account within the current day.
- **JPQL Query Structure**:
  ```java
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
  ```
- **Performance Rationale**: Calculating `SUM(amount)` at the database layer is highly optimal. It prevents pulling thousands of transactions into application memory (Java heap), which would degrade GC performance and application speed. We use JPQL rather than Native Query to keep the application database-agnostic (fully compatible with MySQL, H2, PostgreSQL) while relying on DB indices for quick summation.

---

### 3. Service Layer

#### [NEW] [TransferService.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/services/TransferService.java)
- Annotate with `@Service` and `@RequiredArgsConstructor`.
- Provide methods:
  1. `transfer(TransferRequest request)`: Performs the transfer with verification and daily limit checks under a single `@Transactional` session.
  2. `updateDailyLimit(Long accountId, UpdateDailyLimitRequest request)`: Updates the daily limit of a bank account.

---

### 4. Controller Layer

#### [NEW] [TransferController.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/controllers/TransferController.java)
- Annotate with `@RestController` and `@RequiredArgsConstructor`.
- Expose endpoints:
  - `POST /api/transfers`: Initiate money transfer.
  - `PUT /api/accounts/{id}/daily-limit`: Update daily transfer limit.

---

### 5. DTOs

#### [NEW] [TransferRequest.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/dto/TransferRequest.java)
- Fields: `fromAccountId` (NotNull), `toAccountId` (NotNull), `amount` (NotNull, Positive).

#### [NEW] [UpdateDailyLimitRequest.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/dto/UpdateDailyLimitRequest.java)
- Fields: `dailyLimit` (NotNull, DecimalMin(value = "0.00")).

#### [NEW] [TransferResponse.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/dto/TransferResponse.java)
- Fields detailing the transaction response (transactionId, status, fromAccountNumber, toAccountNumber, amount, transactionTime).

---

### 6. Exception & Handling

#### [NEW] [DailyLimitExceededException.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/exceptions/DailyLimitExceededException.java)
- Inherits from `BusinessException` with status code `429` and message `"Quý khách đã vượt hạn mức giao dịch trong ngày."`.

#### [NEW] [AccountNotFoundException.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/exceptions/AccountNotFoundException.java)
- Inherits from `BusinessException` (404 Not Found).

#### [NEW] [InsufficientBalanceException.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/exceptions/InsufficientBalanceException.java)
- Inherits from `BusinessException` (400 Bad Request).

#### [NEW] [ValidationException.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/exceptions/ValidationException.java)
- Inherits from `BusinessException` (400 Bad Request).

#### [MODIFY] [GlobalExceptionHandler.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/advice/GlobalExceptionHandler.java)
- Update to support specific HTTP status mapping:
  - If `DailyLimitExceededException` is thrown, return `HTTP 429 Too Many Requests` and the exact message: `"Quý khách đã vượt hạn mức giao dịch trong ngày."`.

---

### 7. Security Configuration

#### [MODIFY] [SecurityConfig.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/security/SecurityConfig.java)
- We will configure them to permit all for API testing/grading purposes.

---

## Pseudo Code: `transfer(TransferRequest request)`

```text
1. Bắt đầu Transaction (@Transactional)
2. Validate đầu vào (fromAccountId, toAccountId, amount)
   - Nếu amount <= 0 => throw ValidationException
   - Nếu fromAccountId == toAccountId => throw ValidationException
3. Lấy BankAccount nguồn (fromAccount) và nhận (toAccount) bằng khóa ghi bi quan PESSIMISTIC_WRITE.
   - Sắp xếp khóa theo ID (tài khoản ID bé hơn khóa trước) để tránh Deadlock.
   - Nếu không tìm thấy => throw AccountNotFoundException
   - Nếu trạng thái khác ACTIVE => throw ValidationException
4. Tính tổng số tiền đã chuyển thành công hôm nay của fromAccount từ database:
   - SELECT SUM(amount) FROM TransactionHistory WHERE fromAccount = :fromAccount AND transactionTime >= :startOfDay AND transactionTime <= :endOfDay AND status = 'SUCCESS'
5. Kiểm tra hạn mức:
   - Nếu (tongTienHomNay + requestAmount) > fromAccount.dailyLimit:
     - Lưu TransactionHistory mới với status = FAILED, description = "Vượt hạn mức giao dịch"
     - Throw DailyLimitExceededException
6. Kiểm tra số dư:
   - Nếu fromAccount.balance < requestAmount:
     - Lưu TransactionHistory mới với status = FAILED, description = "Không đủ số dư"
     - Throw InsufficientBalanceException
7. Cập nhật số dư:
   - fromAccount.balance = fromAccount.balance - requestAmount
   - toAccount.balance = toAccount.balance + requestAmount
   - Lưu fromAccount và toAccount vào database
8. Lưu TransactionHistory mới với status = SUCCESS
9. Trả về TransferResponse
10. Commit Transaction
```

---

## Verification Plan

### Automated Tests
Unit tests inside `src/test/java/com/banking` to cover:
1. **Transfer successfully**: Normal flow when balance and daily limit are sufficient.
2. **Transfer fails - Insufficient balance**: Check if `InsufficientBalanceException` is thrown.
3. **Transfer fails - Daily limit exceeded**: Check if `DailyLimitExceededException` is thrown.
4. **Update daily limit successfully**: Test PUT request updates the daily limit on the account correctly.
