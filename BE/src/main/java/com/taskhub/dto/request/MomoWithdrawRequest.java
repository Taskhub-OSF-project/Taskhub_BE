package com.taskhub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** Request rút tiền từ ví TaskHub về ví MoMo */
@Data
public class MomoWithdrawRequest {

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền tối thiểu là 10,000 VND")
    private BigDecimal amount;

    /**
     * Số điện thoại MoMo nhận tiền.
     * Phải là số VN 10 chữ số bắt đầu bằng 0.
     */
    @NotBlank(message = "Số điện thoại MoMo không được để trống")
    @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại không hợp lệ (VD: 0901234567)")
    private String phone;
}
