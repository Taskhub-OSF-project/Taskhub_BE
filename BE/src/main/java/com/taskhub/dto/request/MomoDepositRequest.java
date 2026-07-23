package com.taskhub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Request tạo lệnh nạp tiền qua MoMo */
@Data
public class MomoDepositRequest {

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền tối thiểu là 10,000 VND")
    private BigDecimal amount;

    /** Thông tin mô tả hiển thị trong app MoMo (tuỳ chọn) */
    private String orderInfo;
}
