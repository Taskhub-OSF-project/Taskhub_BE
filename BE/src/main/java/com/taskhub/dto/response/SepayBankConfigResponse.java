package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO trả về thông tin cấu hình tài khoản ngân hàng và tham số nạp rút SePay (VietQR) cho Frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayBankConfigResponse {
    private String bankCode;
    private String bankAccount;
    private String bankName;
    private String accountName;
    private String qrTemplate;
    private BigDecimal minDepositAmount;
    private BigDecimal maxDepositAmount;
}
