package com.taskhub.dto.response;

import com.taskhub.enums.MomoTransactionStatus;
import lombok.*;

import java.math.BigDecimal;

/** Response trả về sau khi yêu cầu rút tiền MoMo */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MomoWithdrawResponse {

    private String orderId;
    private MomoTransactionStatus status;
    private BigDecimal amount;

    /** Số dư ví sau khi trừ (nếu SUCCESS) */
    private BigDecimal newBalance;

    private String message;
    private String momoTransId;
}
