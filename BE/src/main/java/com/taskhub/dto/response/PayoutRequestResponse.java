package com.taskhub.dto.response;

import com.taskhub.entity.PayoutRequest;
import com.taskhub.enums.PayoutStatus;
import lombok.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutRequestResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private BigDecimal amount;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private PayoutStatus status;
    private String statusLabel;
    private String adminNote;
    private Long processedBy;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private String qrUrl;

    public static PayoutRequestResponse from(PayoutRequest entity) {
        String userName = entity.getUser() != null ? entity.getUser().getFullName() : "N/A";
        String userEmail = entity.getUser() != null ? entity.getUser().getEmail() : "N/A";

        String statusLabel;
        switch (entity.getStatus()) {
            case COMPLETED -> statusLabel = "Đã thanh toán";
            case REJECTED -> statusLabel = "Bị từ chối (Đã hoàn tiền)";
            default -> statusLabel = "Chờ Admin chuyển khoản";
        }

        String vietqrBank = normalizeBankCode(entity.getBankCode());
        String cleanAcc = entity.getAccountNumber() != null ? entity.getAccountNumber().replaceAll("\\s+", "") : "";
        String cleanName = entity.getAccountName() != null ? entity.getAccountName().trim().toUpperCase() : "";
        
        long amountVal = entity.getAmount() != null ? entity.getAmount().longValue() : 0L;
        String addInfo = "TASKHUB RUT TIEN PR" + entity.getId();
        
        String encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
        String encodedInfo = URLEncoder.encode(addInfo, StandardCharsets.UTF_8);

        String generatedQrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                vietqrBank, cleanAcc, amountVal, encodedInfo, encodedName
        );

        return PayoutRequestResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userName(userName)
                .userEmail(userEmail)
                .amount(entity.getAmount())
                .bankCode(entity.getBankCode())
                .accountNumber(entity.getAccountNumber())
                .accountName(entity.getAccountName())
                .status(entity.getStatus())
                .statusLabel(statusLabel)
                .adminNote(entity.getAdminNote())
                .processedBy(entity.getProcessedBy())
                .processedAt(entity.getProcessedAt())
                .createdAt(entity.getCreatedAt())
                .qrUrl(generatedQrUrl)
                .build();
    }

    private static String normalizeBankCode(String bank) {
        if (bank == null) return "MB";
        String b = bank.trim().toUpperCase();
        if (b.contains("VIETIN") || b.equals("ICB")) return "ICB";
        if (b.contains("VIETCOM") || b.equals("VCB")) return "VCB";
        if (b.contains("TECHCOM") || b.equals("TCB")) return "TCB";
        if (b.contains("MB") || b.contains("QUAN DOI") || b.contains("MILITARY")) return "MB";
        if (b.contains("ACB") || b.contains("A CHAU")) return "ACB";
        if (b.contains("BIDV") || b.contains("DAU TU")) return "BIDV";
        if (b.contains("VP") || b.contains("THINH VUONG")) return "VPB";
        if (b.contains("TP") || b.contains("TIEN PHONG")) return "TPB";
        if (b.contains("AGRI") || b.contains("NONG NGHIEP")) return "VBA";
        if (b.contains("SACOM") || b.contains("SACOMBANK")) return "STB";
        if (b.contains("MSB") || b.contains("HANG HAI")) return "MSB";
        if (b.contains("VIB") || b.contains("QUOC TE")) return "VIB";
        if (b.contains("SHB") || b.contains("SAI GON HA NOI")) return "SHB";
        if (b.contains("SEAB") || b.contains("DONG NAM A")) return "SEAB";
        return b.replaceAll("\\s+", "");
    }
}
