package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletReadinessResponse {
    private boolean sufficient;
    private BigDecimal budget;
    private BigDecimal platformFee;
    private BigDecimal requiredTotal;
    private BigDecimal currentBalance;
    private BigDecimal shortfall;
    /** FE: redirect user to wallet top-up, then resume create-task flow */
    private String action;
    private String resumeFlow;
}
