package com.taskhub.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO đại diện cho payload JSON được gửi từ Webhook của SePay khi có giao dịch ngân hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayWebhookRequest {

    private Long id;

    private String gateway;

    @JsonAlias({"transaction_date", "transactionDate"})
    private String transactionDate;

    @JsonAlias({"account_number", "accountNumber"})
    private String accountNumber;

    @JsonAlias({"sub_account", "subAccount"})
    private String subAccount;

    private String code;

    @JsonAlias({"content", "transaction_content", "transactionContent", "description"})
    private String content;

    @JsonAlias({"transfer_type", "transferType"})
    private String transferType;

    @JsonAlias({"transfer_amount", "transferAmount", "amount_in", "amount"})
    private BigDecimal transferAmount;

    @JsonAlias({"accumulated", "balance"})
    private BigDecimal accumulated;

    @JsonAlias({"reference_code", "referenceCode", "reference_number", "referenceNumber"})
    private String referenceCode;
}
