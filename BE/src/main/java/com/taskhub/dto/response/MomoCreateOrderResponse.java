package com.taskhub.dto.response;

import com.taskhub.enums.MomoTransactionStatus;
import lombok.*;

import java.math.BigDecimal;

/** Response trả về sau khi tạo lệnh nạp tiền MoMo */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MomoCreateOrderResponse {

    /** Internal order ID (TaskHub) */
    private String orderId;

    /** Deeplink để mở app MoMo trực tiếp (mobile preferred) */
    private String deeplink;

    /** Payment URL (dùng cho WebView hoặc web browser) */
    private String payUrl;

    /** QR Code URL (fallback) */
    private String qrCodeUrl;

    /** Số tiền (VND) */
    private BigDecimal amount;

    /** Trạng thái ban đầu luôn là PENDING */
    private MomoTransactionStatus status;

    /** Mô tả ngắn */
    private String message;
}
