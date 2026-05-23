package com.taskhub.dto.response;

import com.taskhub.enums.WalletTransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTransactionResponse {
    private UUID id;
    private WalletTransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private UUID taskId;
    private LocalDateTime createdAt;
}
