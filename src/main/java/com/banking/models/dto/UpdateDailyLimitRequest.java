package com.banking.models.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDailyLimitRequest {

    @NotNull(message = "Hạn mức hàng ngày không được để trống")
    @DecimalMin(value = "0.0", message = "Hạn mức hàng ngày không được âm")
    private BigDecimal dailyLimit;
}
