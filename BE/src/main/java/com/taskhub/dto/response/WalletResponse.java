package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletResponse {
    private BigDecimal balance;
    /** Whether direct cash simulation endpoints are enabled in this environment. */
    private boolean cashOperationsEnabled;
}
