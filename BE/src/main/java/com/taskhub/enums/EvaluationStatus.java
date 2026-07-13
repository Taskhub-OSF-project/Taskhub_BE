package com.taskhub.enums;

public enum EvaluationStatus {
    /**
     * AI đã phân tích & đối chiếu, chờ Hirer xác nhận.
     */
    AI_ANALYZED,

    /**
     * Hirer đã xác nhận kết quả AI.
     */
    HIRER_CONFIRMED,

    /**
     * Hirer đã chỉnh sửa điểm/sao.
     */
    HIRER_MODIFIED,

    /**
     * Hirer ghi đè hoàn toàn kết quả AI.
     */
    HIRER_OVERRIDDEN
}
