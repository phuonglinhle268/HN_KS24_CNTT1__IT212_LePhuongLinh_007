# Lịch sử Yêu cầu Prompt (Prompt History)

Tài liệu này ghi nhận đầy đủ lịch sử các yêu cầu (Prompts) nhận được từ người dùng và các bước giải quyết tương ứng trong suốt phiên phát triển tính năng **Kiểm soát hạn mức chuyển tiền theo ngày (Daily Transfer Limit Control)**.

---

## Yêu cầu 1: Khởi tạo Tính năng & Phân tích SRS, Database, Lập trình
### Nội dung Prompt nhận được:
> Khách hàng yêu cầu bổ sung tính năng Kiểm soát hạn mức chuyển tiền theo ngày.
>
> **Yêu cầu:**
> 1. Mỗi tài khoản có `DailyLimit`. Ví dụ: 50.000.000 VNĐ.
> 2. API chuyển tiền: Tính tổng số tiền đã chuyển trong ngày hiện tại. Nếu: `Tổng đã chuyển + giao dịch mới > DailyLimit` => chặn giao dịch, ném Exception.
> 3. API tự cập nhật hạn mức `DailyLimit` (tăng/giảm).
> 4. Tối ưu hóa tính tổng giao dịch ngày tại Database (không load lên Java để cộng).
>
> **Yêu cầu thực hiện:**
> - **Bước 1 (Phân tích SRS)**: Mục tiêu, Functional & Non-functional Requirements, Luồng xử lý, Entity cần bổ sung, Quan hệ, API, Exception, Thuật toán DailyLimit.
> - **Bước 2 (Thiết kế Database)**: Entity `TransactionHistory` (id, fromAccount, toAccount, amount, transactionTime, status, createdAt) và thuộc tính bổ sung trong `BankAccount`.
> - **Bước 3 (Thiết kế Repository)**: Viết Query tối ưu để lấy `SUM(amount)` theo account và ngày hiện tại (ưu tiên JPQL).
> - **Bước 4 (Pseudo Code)**: Thuật toán `Transfer()`.
> - **Bước 5 (Lập trình)**: Source code hoàn chỉnh.
>   - `POST /api/transfers`
>   - `PUT /api/accounts/{id}/daily-limit`
>   - Service xử lý giao dịch nguyên tử trong `@Transactional`.
>   - Các Exception: `DailyLimitExceededException`, `AccountNotFoundException`, `InsufficientBalanceException`, `ValidationException`.
>   - Cập nhật `GlobalExceptionHandler` trả về HTTP 429 hoặc 403 khi vượt hạn mức với thông báo: *"Quý khách đã vượt hạn mức giao dịch trong ngày."*
>   - Output gồm: SRS, Thiết kế DB, API, Pseudo code, Source code đầy đủ, giải thích luồng, điểm tối ưu hiệu năng, Test case, Cấu trúc thư mục cuối cùng của dự án chứa file `SRS.md`.

### Giải pháp thực hiện:
- Thiết lập trường `dailyLimit` trong `BankAccount`.
- Tạo mới thực thể `TransactionHistory` ghi log lịch sử giao dịch.
- Tạo `TransactionHistoryRepository` với câu lệnh JPQL gộp `SUM(amount)`.
- Xây dựng `TransferService` xử lý logic kiểm tra hạn mức trước, kiểm tra số dư sau, cập nhật tài khoản và ghi lịch sử giao dịch.
- Tạo các lớp Exception tương ứng và cập nhật bộ xử lý lỗi tập trung `GlobalExceptionHandler`.
- Viết các ca kiểm thử trong `TransferServiceTest.java`.

---

## Yêu cầu 2: Bổ sung Sơ đồ ER Diagram Mermaid và Giải pháp Cộng dồn và Xử lý Concurrency vào SRS
### Nội dung Prompt nhận được:
> trong srs
>cần đảm bảo chỉ ra được cách giải quyết bài toán "Cộng dồn >giao dịch trong ngày" và xử lí daily limit hợp lí

### Giải pháp thực hiện:
- Cập nhật tài liệu `SRS.md` tại **Mục 9**:
  - **Bài toán Cộng dồn ngày**: Giải thích việc sử dụng query JPQL `SUM` ở tầng cơ sở dữ liệu kết hợp tối ưu chỉ mục (Composite Index) trên các cột `(from_account_id, status, transaction_time)` giúp truy vấn đạt hiệu năng $O(\log N)$ thay vì quét toàn bảng $O(N)$.
  - **Bài toán Xử lý Đồng thời**: Áp dụng cơ chế **Pessimistic Locking (Khóa ghi bi quan `PESSIMISTIC_WRITE`)** tại tầng cơ sở dữ liệu để tuần tự hóa các yêu cầu chuyển khoản đồng thời từ cùng một tài khoản, ngăn chặn bypass hạn mức và double-spending.
  - **Đồng bộ hóa Mã nguồn**: Chuyển đổi mã nguồn trong `BankAccountRepository` và `TransferService` sử dụng `findByIdForUpdate` kết hợp sắp xếp khóa theo ID tài khoản để tránh deadlock.

>với phần quan hệ giữa các entity
> Sinh sơ đồ ER Diagram bằng cú pháp Mermaid.
>
> Yêu cầu:
> - Thể hiện đầy đủ các Entity.
> - Thể hiện Cardinality (1-1, 1-N, N-1).
> - Ghi rõ tên quan hệ (owns, sender, receiver,...).
> - Bao gồm các thuộc tính chính của từng Entity.
> - Chỉ hiển thị các thuộc tính quan trọng như:
>   - Primary Key
>   - Foreign Key
>   - Các field nghiệp vụ chính.
> - Định dạng đúng chuẩn Mermaid
> Đồng thời đảm bảo toàn bộ source code đúng logic, hoàn chỉnh, đảm bảo clean code, đúng yêu cầu

### Giải pháp thực hiện:
- Thêm sơ đồ ER Diagram viết bằng cú pháp `erDiagram` của Mermaid vào **Mục 6** của `SRS.md`.
- Sơ đồ thể hiện đầy đủ các thực thể `CUSTOMER`, `BANK_ACCOUNT`, `TRANSACTION_HISTORY` kèm các thuộc tính PK (`id`), FK (`customerId`, `fromAccountId`, `toAccountId`) và các trường nghiệp vụ chính.
- Biểu diễn quan hệ Cardinality một - nhiều (`||--o{`) và gắn nhãn liên kết tương ứng (`owns`, `sends (fromAccount)`, `receives (toAccount)`).

---

## Yêu cầu 3: Bổ sung các File Quản lý và Kế hoạch vào Dự án
### Nội dung Prompt nhận được:
> bổ sung trong dự án file walkthrough.md, Prompt_History.md (file lịch sử prompt) và implementation plan

### Giải pháp thực hiện:
- Tạo mới và lưu trữ trực tiếp các tệp `walkthrough.md`, `implementation_plan.md` và `Prompt_History.md` tại thư mục gốc của dự án.
- Đảm bảo đầy đủ nội dung mô tả chi tiết kế hoạch thực hiện, hướng dẫn kiểm thử và lịch sử thay đổi để đóng gói cùng sản phẩm bàn giao.

