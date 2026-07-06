package com.banking.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "Tài khoản nguồn không được để trống")
    private Long fromAccountId;

    @NotNull(message = "Tài khoản đích không được để trống")
    private Long toAccountId;

    @NotNull(message = "Số tiền không được để trống")
    @Positive(message = "Số tiền chuyển phải lớn hơn 0")
    private BigDecimal amount;
}
