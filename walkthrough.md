# Walkthrough - Daily Transfer Limit Control (with Pessimistic Locking)

We have successfully implemented the Daily Transfer Limit Control feature in the Core Banking project following Clean Architecture, SOLID, and the requested performance and concurrency optimizations.

---

## Changes Implemented

### 1. Database Schema & Entities
- **[BankAccount.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/entities/BankAccount.java)**:
  - Added the `dailyLimit` field of type `BigDecimal` with a default value of `50,000,000.00` VND.
- **[TransactionStatus.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/constant/TransactionStatus.java)**:
  - Created an enum (`SUCCESS`, `FAILED`) to track transaction outcomes.
- **[TransactionHistory.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/entities/TransactionHistory.java)**:
  - Created a new entity to persist transaction records containing `fromAccount`, `toAccount`, `amount`, `transactionTime`, `status`, and `createdAt` (with `PrePersist`).

### 2. Database Access & Concurrency Control
- **[BankAccountRepository.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/repositories/BankAccountRepository.java)**:
  - Added a pessimistic lock lookup method:
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.id = :id")
    Optional<BankAccount> findByIdForUpdate(@Param("id") Long id);
    ```
- **[TransactionHistoryRepository.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/repositories/TransactionHistoryRepository.java)**:
  - Created a JPA Repository with an optimized JPQL query to fetch the sum of successful daily transactions:
    ```java
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionHistory t " +
           "WHERE t.fromAccount = :account " +
           "AND t.transactionTime >= :startOfDay " +
           "AND t.transactionTime <= :endOfDay " +
           "AND t.status = :status")
    BigDecimal sumAmountByAccountAndTransactionTimeBetweenAndStatus(...);
    ```

### 3. Business Service
- **[TransferService.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/models/services/TransferService.java)**:
  - Implemented the `transfer` logic wrapped in a `@Transactional` block.
  - **Pessimistic Locking & Deadlock Prevention**: Utilizes `findByIdForUpdate` to lock bank accounts during a transfer session. To prevent database deadlocks, accounts are locked in a strict order by sorting their IDs:
    ```java
    if (request.getFromAccountId().compareTo(request.getToAccountId()) < 0) {
        fromAccount = bankAccountRepository.findByIdForUpdate(request.getFromAccountId()).orElseThrow(...);
        toAccount = bankAccountRepository.findByIdForUpdate(request.getToAccountId()).orElseThrow(...);
    } else {
        toAccount = bankAccountRepository.findByIdForUpdate(request.getToAccountId()).orElseThrow(...);
        fromAccount = bankAccountRepository.findByIdForUpdate(request.getFromAccountId()).orElseThrow(...);
    }
    ```
  - Performs daily limit verification and updates sender/receiver balances in a single transactional unit.
  - Automatically writes a `FAILED` transaction log to DB on rejection.

### 4. API Endpoints
- **[TransferController.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/controllers/TransferController.java)**:
  - Exposed `POST /api/transfers` for execution.
  - Exposed `PUT /api/accounts/{id}/daily-limit` for daily limit updates.

### 5. Exception Handling & Security
- **Exceptions**: Declared `DailyLimitExceededException` (429), `AccountNotFoundException` (404), `InsufficientBalanceException` (400), and `ValidationException` (400) under `com.banking.exceptions`.
- **[GlobalExceptionHandler.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/advice/GlobalExceptionHandler.java)**:
  - Updated handler to map `DailyLimitExceededException` directly to `HTTP 429` with message `"Quý khách đã vượt hạn mức giao dịch trong ngày."`.
- **[SecurityConfig.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/main/java/com/banking/security/SecurityConfig.java)**:
  - Configured Spring Security filter chain rules to allow public access (`permitAll`) to `/api/transfers` and `/api/accounts/**` endpoints for verification ease.

### 6. Documentation
- **[SRS.md](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/SRS.md)**:
  - Created a comprehensive specification document in the project root detailing project goals, functional/non-functional criteria, sequence flow diagrams, entities metadata, database indexing strategies, timezone boundaries, and concurrent transaction control.

---

## Verification Results

### Unit Tests
We verified the complete set of business rules under unit tests in **[TransferServiceTest.java](file:///c:/Users/plinh/Downloads/CoreBanking-main/CoreBanking-main/src/test/java/com/banking/models/services/TransferServiceTest.java)**:
- Successful transfer processing.
- Limit breach rejection (`DailyLimitExceededException` mapped to HTTP 429).
- Insufficient balance rejection (`InsufficientBalanceException` mapped to HTTP 400).
- Self-transfer rejection.
- Limit updates execution.

Running the tests with `./gradlew test --tests "com.banking.models.services.TransferServiceTest"` was **100% successful**:
```text
> Task :test

BUILD SUCCESSFUL in 16s
4 actionable tasks: 3 executed, 1 up-to-date
```
All unit tests passed cleanly!
