package com.taskhub.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EscrowCalculator {
    public static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");
    public static final BigDecimal PLATFORM_FEE_PERCENT = new BigDecimal("5");

    private EscrowCalculator() {}

    public static BigDecimal platformFee(BigDecimal budget) {
        return budget.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalEscrowDeduction(BigDecimal budget) {
        return budget.add(platformFee(budget));
    }

    public static BigDecimal getPlatformFeePercent() {
        return PLATFORM_FEE_PERCENT;
    }
}
