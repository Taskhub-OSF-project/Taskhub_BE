package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sepay_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SepayWebhookLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String gateway;

    @Column(name = "transaction_date", length = 50)
    private String transactionDate;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "sub_account", length = 50)
    private String subAccount;

    @Column(name = "amount_in")
    private BigDecimal amountIn;

    @Column(name = "amount_out")
    private BigDecimal amountOut;

    private BigDecimal accumulated;

    @Column(length = 100)
    private String code;

    @Column(name = "transaction_content", columnDefinition = "TEXT")
    private String transactionContent;

    @Column(name = "reference_number", length = 100, unique = true)
    private String referenceNumber;

    @Column(name = "body_json", columnDefinition = "TEXT")
    private String bodyJson;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "RECEIVED";

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
