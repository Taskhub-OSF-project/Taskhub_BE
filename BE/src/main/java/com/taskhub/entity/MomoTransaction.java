package com.taskhub.entity;

import com.taskhub.enums.MomoTransactionStatus;
import com.taskhub.enums.MomoTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lưu lịch sử lệnh thanh toán MoMo (nạp tiền / rút tiền).
 * Tách biệt với WalletTransaction để giữ audit trail đầy đủ
 * kể cả các lệnh PENDING hoặc FAILED.
 */
@Entity
@Table(name = "momo_transactions", indexes = {
    @Index(name = "idx_momo_tx_order_id", columnList = "order_id", unique = true),
    @Index(name = "idx_momo_tx_user_id",  columnList = "user_id"),
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MomoTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Order ID do backend sinh ra, gửi sang MoMo */
    @Column(name = "order_id", nullable = false, length = 50, unique = true)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MomoTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MomoTransactionStatus status;

    /** Số tiền giao dịch (VND) */
    @Column(precision = 15, scale = 0, nullable = false)
    private BigDecimal amount;

    /** MoMo transaction ID trả về sau khi thanh toán thành công */
    @Column(name = "momo_trans_id", length = 100)
    private String momoTransId;

    /** Deeplink / payUrl MoMo trả về để mở app MoMo */
    @Column(name = "pay_url", length = 512)
    private String payUrl;

    @Column(name = "deeplink", length = 512)
    private String deeplink;

    /** QR code URL (nếu cần fallback WebView/web) */
    @Column(name = "qr_code_url", length = 512)
    private String qrCodeUrl;

    /** Số điện thoại MoMo nhận tiền (chỉ dùng cho withdrawal) */
    @Column(name = "phone", length = 15)
    private String phone;

    /** Message lỗi từ MoMo (nếu có) */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** Result code từ MoMo callback */
    @Column(name = "result_code")
    private Integer resultCode;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
