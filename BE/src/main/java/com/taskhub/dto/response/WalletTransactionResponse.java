package com.taskhub.dto.response;

import com.taskhub.enums.WalletTransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTransactionResponse {
    private Long id;
    private WalletTransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long taskId;
    private LocalDateTime createdAt;
}
