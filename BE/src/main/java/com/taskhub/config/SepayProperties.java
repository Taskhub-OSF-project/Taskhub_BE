package com.taskhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Cấu hình tham số tích hợp SePay (VietQR & Webhook tự động hoá thanh toán).
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.sepay")
public class SepayProperties {
    /**
     * API Token / Secret key để xác thực Webhook gửi về từ server SePay.
     * Cung cấp qua header: Authorization: Bearer <token> hoặc API-Key.
     */
    private String apiToken;

    /**
     * Mã ngân hàng (Bank Code / BIN) chuẩn của VietQR (VD: MB, VCB, TCB hoặc 970422).
     */
    private String bankCode = "MB";

    /**
     * Số tài khoản ngân hàng của platform dùng để nhận nạp tiền qua VietQR.
     */
    private String bankAccount;

    /**
     * Tên ngân hàng (VD: MBBank, VCB, VietinBank...).
     */
    private String bankName;

    /**
     * Tên chủ tài khoản ngân hàng (VD: CTY TASKHUB hoặc NGUYEN VAN A).
     */
    private String accountName;

    /**
     * Mẫu hiển thị QR code từ VietQR / SePay (VD: compact, print, qr_only).
     */
    private String qrTemplate = "compact";

    private BigDecimal minDepositAmount = BigDecimal.valueOf(10000);
    private BigDecimal maxDepositAmount = BigDecimal.valueOf(50000000);
    private BigDecimal minWithdrawAmount = BigDecimal.valueOf(50000);
    private BigDecimal maxWithdrawAmount = BigDecimal.valueOf(100000000);
}
