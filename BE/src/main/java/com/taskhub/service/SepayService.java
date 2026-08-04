package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.config.SepayProperties;
import com.taskhub.dto.request.SepayWebhookRequest;
import com.taskhub.entity.SepayWebhookLog;
import com.taskhub.entity.User;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.SepayWebhookLogRepository;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service xử lý tích hợp thanh toán và tự động cộng tiền từ Webhook của SePay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayService {

    private final SepayProperties sepayProperties;
    private final SepayWebhookLogRepository sepayLogRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    // Biểu thức chính quy bóc tách ID người dùng hoặc Mã nạp tiền (VD: THTT 123,
    // TASKHUB 105, THTT105)
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?i)(?:TASKHUB|THTT)\\s*(?:USER|U)?\\s*(\\d+)");

    @Transactional
    public void processWebhook(SepayWebhookRequest request, String authorizationHeader) {
        log.info("Received SePay webhook: gateway={}, amount={}, ref={}, content={}",
                request.getGateway(), request.getTransferAmount(), request.getReferenceCode(), request.getContent());

        // 1. Xác thực Token
        verifySecurityToken(authorizationHeader);

        // 2. Chuẩn hóa mã tham chiếu (đảm bảo biến final để dùng trong lambda)
        String rawRef = request.getReferenceCode();
        final String refCode = (rawRef == null || rawRef.isBlank())
                ? "SEPAY_" + (request.getId() != null ? request.getId() : System.currentTimeMillis())
                : rawRef;

        // 3. Kiểm tra Idempotency (Tránh xử lý 2 lần nếu webhook gửi lặp lại)
        Optional<SepayWebhookLog> existingLog = sepayLogRepository.findByReferenceNumber(refCode);
        if (existingLog.isPresent() && "PROCESSED".equals(existingLog.get().getStatus())) {
            log.info("SePay transaction ref={} was already processed. Ignoring duplicate webhook.", refCode);
            return;
        }

        // 4. Lưu log Webhook ban đầu
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            rawJson = "{}";
        }

        SepayWebhookLog logRecord = existingLog.orElseGet(() -> SepayWebhookLog.builder()
                .referenceNumber(refCode)
                .build());

        logRecord.setGateway(request.getGateway());
        logRecord.setTransactionDate(request.getTransactionDate());
        logRecord.setAccountNumber(request.getAccountNumber());
        logRecord.setSubAccount(request.getSubAccount());
        logRecord.setCode(request.getCode());
        logRecord.setTransactionContent(request.getContent());
        logRecord.setAccumulated(request.getAccumulated());
        logRecord.setBodyJson(rawJson);

        boolean isInward = "in".equalsIgnoreCase(request.getTransferType()) ||
                (request.getTransferType() == null && request.getTransferAmount() != null
                        && request.getTransferAmount().compareTo(BigDecimal.ZERO) > 0);

        if (isInward) {
            logRecord.setAmountIn(request.getTransferAmount());
            logRecord.setAmountOut(BigDecimal.ZERO);
        } else {
            logRecord.setAmountIn(BigDecimal.ZERO);
            logRecord.setAmountOut(request.getTransferAmount());
            logRecord.setStatus("IGNORED");
            logRecord.setErrorMessage("Outgoing transfer ignored");
            sepayLogRepository.save(logRecord);
            return;
        }

        // 5. Bóc tách User ID và cộng tiền ví
        Long userId = extractUserId(request.getContent(), request.getCode());
        if (userId == null) {
            log.warn("SePay deposit ignoring: Could not parse User ID from content: {}", request.getContent());
            logRecord.setStatus("IGNORED");
            logRecord.setErrorMessage(
                    "Không bóc tách được User ID (Cú pháp chuẩn: TH <UserId>) từ: " + request.getContent());
            sepayLogRepository.save(logRecord);
            return;
        }

        Optional<User> optionalUser = userRepository.findByIdForUpdate(userId);
        if (optionalUser.isEmpty()) {
            log.warn("SePay deposit ignoring: User ID {} not found in database", userId);
            logRecord.setStatus("ERROR");
            logRecord.setErrorMessage("User ID " + userId + " không tồn tại trong hệ thống.");
            sepayLogRepository.save(logRecord);
            return;
        }

        User user = optionalUser.get();
        BigDecimal amount = request.getTransferAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logRecord.setStatus("IGNORED");
            logRecord.setErrorMessage("Số tiền giao dịch không hợp lệ.");
            sepayLogRepository.save(logRecord);
            return;
        }

        // Thực hiện cộng số dư cho người dùng
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);

        // Ghi lại lịch sử giao dịch ví (Ledger)
        walletService.recordTransaction(user, WalletTransactionType.top_up, amount, null);

        logRecord.setStatus("PROCESSED");
        logRecord.setErrorMessage(null);
        sepayLogRepository.save(logRecord);

        log.info("SePay deposit SUCCESS: Added {} VND to userId={}'s wallet. New balance={}",
                amount, userId, user.getWalletBalance());
    }

    private void verifySecurityToken(String authHeader) {
        String configuredToken = sepayProperties.getApiToken();
        // Nếu server chưa cấu hình apiToken (trống), tạm thời bỏ qua xác thực cho môi
        // trường dev/chưa thiết lập
        if (configuredToken == null || configuredToken.isBlank()) {
            log.debug("SePay API Token is not configured; skipping verification.");
            return;
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Missing Authorization / API-Key header on SePay webhook");
            throw TaskHubException.forbidden("Unauthorized SePay Webhook - Missing Token");
        }

        String providedToken = authHeader;
        if (providedToken.startsWith("Bearer ")) {
            providedToken = providedToken.substring(7).trim();
        } else if (providedToken.startsWith("Apikey ")) {
            providedToken = providedToken.substring(7).trim();
        }

        if (!configuredToken.equals(providedToken)) {
            log.warn("Invalid SePay Webhook Token received: {}", providedToken);
            throw TaskHubException.forbidden("Unauthorized SePay Webhook - Invalid Token");
        }
    }

    /**
     * Tìm kiếm ID người dùng trong chuỗi nội dung giao dịch hoặc mã rút gọn từ
     * SePay.
     */
    private Long extractUserId(String content, String code) {
        if (content != null) {
            Matcher m = USER_ID_PATTERN.matcher(content);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException e) {
                    // Ignore and try next
                }
            }
        }
        if (code != null) {
            Matcher m = USER_ID_PATTERN.matcher(code);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            // Nếu trường code thuần túy chỉ là số
            if (code.matches("^\\d+$")) {
                try {
                    return Long.parseLong(code);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return null;
    }
}
