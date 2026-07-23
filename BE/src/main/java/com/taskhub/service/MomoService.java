package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.config.MomoProperties;
import com.taskhub.dto.request.MomoDepositRequest;
import com.taskhub.dto.request.MomoWithdrawRequest;
import com.taskhub.dto.response.MomoCreateOrderResponse;
import com.taskhub.dto.response.MomoWithdrawResponse;
import com.taskhub.entity.MomoTransaction;
import com.taskhub.entity.User;
import com.taskhub.enums.MomoTransactionStatus;
import com.taskhub.enums.MomoTransactionType;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.MomoTransactionRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý tích hợp MoMo Payment Gateway.
 * <p>
 * Luồng NẠP TIỀN:
 *   1. Client gọi createDepositOrder() → backend tạo lệnh MoMo, lưu PENDING
 *   2. Client mở deeplink momo:// trên thiết bị
 *   3. User thanh toán trong app MoMo
 *   4. MoMo POST callback đến /api/momo/deposit/callback
 *   5. Backend xác thực HMAC, cộng tiền ví, cập nhật trạng thái SUCCESS
 * <p>
 * Luồng RÚT TIỀN:
 *   1. Client gọi requestWithdrawal()
 *   2. Backend kiểm tra số dư, trừ tạm, gọi MoMo Disbursement API
 *   3. Nếu thành công → ghi ledger, trả SUCCESS
 *   4. Nếu thất bại → hoàn lại số dư
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomoService {

    private final MomoProperties momoProperties;
    private final MomoTransactionRepository momoTxRepo;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final DateTimeFormatter ORDER_ID_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ─────────────────────────────────────────────────────────────────────────
    // NẠP TIỀN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bước 1 – Tạo lệnh nạp tiền, trả về deeplink/payUrl để client mở MoMo.
     */
    @Transactional
    public MomoCreateOrderResponse createDepositOrder(MomoDepositRequest request) {
        Long userId = AuthUtil.getCurrentUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        validateDepositAmount(request.getAmount());

        String orderId = generateOrderId("DEP");
        String requestId = UUID.randomUUID().toString();
        String orderInfo = request.getOrderInfo() != null && !request.getOrderInfo().isBlank()
                ? request.getOrderInfo()
                : "Nap tien vi TaskHub - " + user.getFullName();

        // Build payload gửi sang MoMo
        Map<String, Object> body = buildCreateOrderPayload(orderId, requestId,
                request.getAmount(), orderInfo);

        // Gọi MoMo API
        Map<String, Object> momoResponse = callMomoApi(momoProperties.getCreateOrderEndpoint(), body);

        // Lưu transaction PENDING
        MomoTransaction tx = MomoTransaction.builder()
                .orderId(orderId)
                .user(user)
                .type(MomoTransactionType.DEPOSIT)
                .status(MomoTransactionStatus.PENDING)
                .amount(request.getAmount())
                .payUrl(getString(momoResponse, "payUrl"))
                .deeplink(getString(momoResponse, "deeplink"))
                .qrCodeUrl(getString(momoResponse, "qrCodeUrl"))
                .resultCode(getInt(momoResponse, "resultCode"))
                .build();
        momoTxRepo.save(tx);

        log.info("MoMo deposit order created: orderId={}, userId={}, amount={}",
                orderId, userId, request.getAmount());

        return MomoCreateOrderResponse.builder()
                .orderId(orderId)
                .deeplink(tx.getDeeplink())
                .payUrl(tx.getPayUrl())
                .qrCodeUrl(tx.getQrCodeUrl())
                .amount(request.getAmount())
                .status(MomoTransactionStatus.PENDING)
                .message("Mở app MoMo để hoàn thành thanh toán")
                .build();
    }

    /**
     * Bước 4 – Nhận callback từ MoMo sau khi user thanh toán.
     * Idempotent: callback trùng lặp không cộng tiền 2 lần.
     */
    @Transactional
    public void handleDepositCallback(Map<String, String> params) {
        String orderId     = params.get("orderId");
        String signature   = params.get("signature");
        int    resultCode  = Integer.parseInt(params.getOrDefault("resultCode", "-1"));
        String momoTransId = params.get("transId");

        log.info("MoMo callback received: orderId={}, resultCode={}", orderId, resultCode);

        // 1. Tìm transaction
        MomoTransaction tx = momoTxRepo.findByOrderId(orderId)
                .orElseThrow(() -> {
                    log.warn("MoMo callback for unknown orderId: {}", orderId);
                    return TaskHubException.notFound("Order not found: " + orderId);
                });

        // 2. Idempotency – bỏ qua nếu đã xử lý
        if (tx.getStatus() != MomoTransactionStatus.PENDING) {
            log.info("MoMo callback already processed for orderId={}, status={}", orderId, tx.getStatus());
            return;
        }

        // 3. Xác thực chữ ký HMAC-SHA256
        String rawHash = buildCallbackSignatureRaw(params);
        String expectedSig = hmacSha256(momoProperties.getSecretKey(), rawHash);
        if (!expectedSig.equals(signature)) {
            log.warn("MoMo callback signature mismatch for orderId={}", orderId);
            tx.setStatus(MomoTransactionStatus.FAILED);
            tx.setErrorMessage("Invalid signature");
            momoTxRepo.save(tx);
            throw TaskHubException.forbidden("Invalid MoMo signature");
        }

        // 4. Xử lý kết quả
        tx.setMomoTransId(momoTransId);
        tx.setResultCode(resultCode);
        tx.setUpdatedAt(LocalDateTime.now());

        if (resultCode == 0) {
            // Thành công – cộng tiền ví
            User user = userRepository.findByIdForUpdate(tx.getUser().getId())
                    .orElseThrow(() -> TaskHubException.notFound("User not found"));
            user.setWalletBalance(user.getWalletBalance().add(tx.getAmount()));
            userRepository.save(user);
            walletService.recordTransaction(user, WalletTransactionType.top_up, tx.getAmount(), null);
            tx.setStatus(MomoTransactionStatus.SUCCESS);
            log.info("MoMo deposit SUCCESS: orderId={}, userId={}, amount={}",
                    orderId, tx.getUser().getId(), tx.getAmount());
        } else {
            tx.setStatus(resultCode == 1006 ? MomoTransactionStatus.CANCELLED : MomoTransactionStatus.FAILED);
            tx.setErrorMessage("MoMo resultCode=" + resultCode);
            log.info("MoMo deposit FAILED/CANCELLED: orderId={}, resultCode={}", orderId, resultCode);
        }

        momoTxRepo.save(tx);
    }

    /**
     * Kiểm tra trạng thái lệnh nạp tiền (polling từ mobile sau khi user quay lại app).
     */
    public MomoTransactionStatus getDepositStatus(String orderId) {
        Long userId = AuthUtil.getCurrentUser().getId();
        MomoTransaction tx = momoTxRepo.findByOrderId(orderId)
                .orElseThrow(() -> TaskHubException.notFound("Order not found"));
        if (!tx.getUser().getId().equals(userId)) {
            throw TaskHubException.forbidden("Access denied");
        }
        return tx.getStatus();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RÚT TIỀN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý yêu cầu rút tiền từ ví TaskHub về ví MoMo.
     */
    @Transactional
    public MomoWithdrawResponse requestWithdrawal(MomoWithdrawRequest request) {
        Long userId = AuthUtil.getCurrentUser().getId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        validateWithdrawAmount(request.getAmount());

        // Kiểm tra số dư
        if (user.getWalletBalance().compareTo(request.getAmount()) < 0) {
            throw TaskHubException.badRequest("Số dư ví không đủ để rút. Hiện có: "
                    + user.getWalletBalance() + " VND");
        }

        String orderId = generateOrderId("WIT");

        // Trừ tiền ví trước (sẽ hoàn lại nếu MoMo thất bại)
        user.setWalletBalance(user.getWalletBalance().subtract(request.getAmount()));
        userRepository.save(user);

        // Tạo MomoTransaction PENDING
        MomoTransaction tx = MomoTransaction.builder()
                .orderId(orderId)
                .user(user)
                .type(MomoTransactionType.WITHDRAWAL)
                .status(MomoTransactionStatus.PENDING)
                .amount(request.getAmount())
                .phone(request.getPhone())
                .build();
        momoTxRepo.save(tx);

        // Gọi MoMo Disbursement API
        try {
            Map<String, Object> disburseBody = buildDisbursePayload(orderId, request);
            Map<String, Object> momoResp = callMomoApi(momoProperties.getDisburseEndpoint(), disburseBody);
            int resultCode = getInt(momoResp, "resultCode");

            if (resultCode == 0) {
                // Thành công
                String momoTransId = getString(momoResp, "transId");
                tx.setStatus(MomoTransactionStatus.SUCCESS);
                tx.setMomoTransId(momoTransId);
                tx.setResultCode(resultCode);
                walletService.recordTransaction(user, WalletTransactionType.withdrawal,
                        request.getAmount().negate(), null);
                momoTxRepo.save(tx);
                log.info("MoMo withdrawal SUCCESS: orderId={}, userId={}, amount={}",
                        orderId, userId, request.getAmount());
                return MomoWithdrawResponse.builder()
                        .orderId(orderId)
                        .status(MomoTransactionStatus.SUCCESS)
                        .amount(request.getAmount())
                        .newBalance(user.getWalletBalance())
                        .momoTransId(momoTransId)
                        .message("Rút tiền thành công! Tiền sẽ về ví MoMo trong vài phút.")
                        .build();
            } else {
                // Thất bại – hoàn tiền
                String errMsg = getString(momoResp, "message");
                rollbackWithdrawal(user, tx, request.getAmount(), resultCode, errMsg);
                throw TaskHubException.badRequest("Rút tiền thất bại: " + errMsg);
            }
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            log.error("MoMo withdrawal API error: orderId={}", orderId, e);
            rollbackWithdrawal(user, tx, request.getAmount(), -1, e.getMessage());
            throw TaskHubException.internalError("Lỗi kết nối MoMo, vui lòng thử lại");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers – Payload builders
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildCreateOrderPayload(String orderId, String requestId,
                                                         BigDecimal amount, String orderInfo) {
        String partnerCode = momoProperties.getPartnerCode();
        String accessKey   = momoProperties.getAccessKey();
        String returnUrl   = momoProperties.getReturnUrl();
        String ipnUrl      = momoProperties.getIpnUrl();
        long   amountLong  = amount.longValue();

        // Raw signature string theo tài liệu MoMo v2
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amountLong
                + "&extraData="
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + returnUrl
                + "&requestId=" + requestId
                + "&requestType=captureWallet";

        String signature = hmacSha256(momoProperties.getSecretKey(), rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("partnerName", momoProperties.getPartnerName());
        body.put("storeId", partnerCode);
        body.put("requestId", requestId);
        body.put("amount", amountLong);
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", returnUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("lang", "vi");
        body.put("extraData", "");
        body.put("requestType", "captureWallet");
        body.put("signature", signature);
        return body;
    }

    private Map<String, Object> buildDisbursePayload(String orderId, MomoWithdrawRequest request) {
        String partnerCode = momoProperties.getPartnerCode();
        String accessKey   = momoProperties.getAccessKey();
        String requestId   = UUID.randomUUID().toString();
        long   amountLong  = request.getAmount().longValue();

        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amountLong
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId;

        String signature = hmacSha256(momoProperties.getSecretKey(), rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("requestId", requestId);
        body.put("amount", amountLong);
        body.put("orderId", orderId);
        body.put("receiverNumber", request.getPhone());
        body.put("receiverType", "0"); // 0 = MoMo wallet
        body.put("paymentCode", "");
        body.put("lang", "vi");
        body.put("description", "Rut tien vi TaskHub");
        body.put("signature", signature);
        return body;
    }

    /**
     * Tạo raw string để xác thực HMAC callback từ MoMo.
     * Tham số theo thứ tự alphabetical theo tài liệu MoMo.
     */
    private String buildCallbackSignatureRaw(Map<String, String> p) {
        return "accessKey=" + momoProperties.getAccessKey()
                + "&amount=" + p.getOrDefault("amount", "")
                + "&extraData=" + p.getOrDefault("extraData", "")
                + "&message=" + p.getOrDefault("message", "")
                + "&orderId=" + p.getOrDefault("orderId", "")
                + "&orderInfo=" + p.getOrDefault("orderInfo", "")
                + "&orderType=" + p.getOrDefault("orderType", "")
                + "&partnerCode=" + p.getOrDefault("partnerCode", "")
                + "&payType=" + p.getOrDefault("payType", "")
                + "&requestId=" + p.getOrDefault("requestId", "")
                + "&responseTime=" + p.getOrDefault("responseTime", "")
                + "&resultCode=" + p.getOrDefault("resultCode", "")
                + "&transId=" + p.getOrDefault("transId", "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers – HTTP & Crypto
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMomoApi(String endpoint, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("MoMo API response status={}, body={}", resp.statusCode(), resp.body());
            return objectMapper.readValue(resp.body(), Map.class);
        } catch (Exception e) {
            log.error("MoMo API call failed: endpoint={}", endpoint, e);
            throw TaskHubException.internalError("Không thể kết nối đến MoMo");
        }
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 signing failed", e);
        }
    }

    private String generateOrderId(String prefix) {
        return prefix + "_" + LocalDateTime.now().format(ORDER_ID_FMT)
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private void rollbackWithdrawal(User user, MomoTransaction tx,
                                    BigDecimal amount, int resultCode, String errMsg) {
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);
        tx.setStatus(MomoTransactionStatus.FAILED);
        tx.setResultCode(resultCode);
        tx.setErrorMessage(errMsg);
        tx.setUpdatedAt(LocalDateTime.now());
        momoTxRepo.save(tx);
        log.warn("MoMo withdrawal ROLLED BACK: orderId={}, reason={}", tx.getOrderId(), errMsg);
    }

    private void validateDepositAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw TaskHubException.badRequest("Số tiền phải lớn hơn 0");
        if (amount.longValue() < momoProperties.getMinDepositAmount())
            throw TaskHubException.badRequest("Số tiền nạp tối thiểu là "
                    + momoProperties.getMinDepositAmount() + " VND");
        if (amount.longValue() > momoProperties.getMaxDepositAmount())
            throw TaskHubException.badRequest("Số tiền nạp tối đa là "
                    + momoProperties.getMaxDepositAmount() + " VND");
    }

    private void validateWithdrawAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw TaskHubException.badRequest("Số tiền phải lớn hơn 0");
        if (amount.longValue() < momoProperties.getMinWithdrawAmount())
            throw TaskHubException.badRequest("Số tiền rút tối thiểu là "
                    + momoProperties.getMinWithdrawAmount() + " VND");
        if (amount.longValue() > momoProperties.getMaxWithdrawAmount())
            throw TaskHubException.badRequest("Số tiền rút tối đa là "
                    + momoProperties.getMaxWithdrawAmount() + " VND");
    }

    @SuppressWarnings("unchecked")
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return -1;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return -1; }
    }
}
