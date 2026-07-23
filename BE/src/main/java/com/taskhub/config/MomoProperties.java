package com.taskhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình MoMo Payment Gateway.
 * Đọc từ application.yml dưới prefix app.momo.*
 * Sử dụng MoMo Sandbox mặc định; thay bằng endpoint production khi live.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.momo")
public class MomoProperties {

    /** MoMo Partner Code (cấp bởi MoMo Business) */
    private String partnerCode = "MOMO";

    /** Access key từ MoMo Partner Portal */
    private String accessKey = "F8BBA842ECF85";

    /** Secret key để ký HMAC-SHA256 */
    private String secretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz";

    /** Tên partner hiển thị trong app MoMo */
    private String partnerName = "TaskHub";

    /** MoMo API endpoint tạo payment order */
    private String createOrderEndpoint = "https://test-payment.momo.vn/v2/gateway/api/create";

    /** MoMo Disbursement API endpoint (rút tiền) */
    private String disburseEndpoint = "https://test-payment.momo.vn/v2/gateway/api/disbursement";

    /**
     * URL backend nhận callback từ MoMo sau khi user thanh toán.
     * Phải là URL public (dùng ngrok trong dev nếu test thực với MoMo Sandbox).
     */
    private String ipnUrl = "http://localhost:8080/api/momo/deposit/callback";

    /**
     * URL redirect sau khi user thanh toán xong trên web/WebView.
     * Với deeplink mobile, MoMo sẽ redirect về app sau khi thanh toán.
     */
    private String returnUrl = "taskhub://wallet/deposit/result";

    /** Giới hạn nạp tối thiểu (VND) */
    private long minDepositAmount = 10_000L;

    /** Giới hạn nạp tối đa mỗi lần (VND) */
    private long maxDepositAmount = 50_000_000L;

    /** Giới hạn rút tối thiểu (VND) */
    private long minWithdrawAmount = 10_000L;

    /** Giới hạn rút tối đa mỗi lần (VND) */
    private long maxWithdrawAmount = 10_000_000L;
}
