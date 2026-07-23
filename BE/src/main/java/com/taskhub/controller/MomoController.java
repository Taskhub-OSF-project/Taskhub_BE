package com.taskhub.controller;

import com.taskhub.config.OpenApiConfig;
import com.taskhub.dto.request.MomoDepositRequest;
import com.taskhub.dto.request.MomoWithdrawRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.MomoCreateOrderResponse;
import com.taskhub.dto.response.MomoWithdrawResponse;
import com.taskhub.enums.MomoTransactionStatus;
import com.taskhub.service.MomoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MoMo Payment Gateway endpoints.
 *
 * <p>Public (không cần JWT):
 * <ul>
 *   <li>POST /api/momo/deposit/callback – IPN từ MoMo server</li>
 *   <li>GET  /api/momo/deposit/return   – redirect sau thanh toán web</li>
 * </ul>
 *
 * <p>Protected (cần JWT):
 * <ul>
 *   <li>POST /api/momo/deposit/create     – tạo lệnh nạp tiền</li>
 *   <li>GET  /api/momo/deposit/{orderId}  – kiểm tra trạng thái lệnh nạp</li>
 *   <li>POST /api/momo/withdraw/request   – yêu cầu rút tiền</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/momo")
@RequiredArgsConstructor
@Tag(name = "MoMo Payment", description = "Nạp / Rút tiền qua MoMo")
public class MomoController {

    private final MomoService momoService;

    // ─── Nạp tiền ────────────────────────────────────────────────────────────

    @Operation(summary = "Tạo lệnh nạp tiền MoMo",
               description = "Trả về deeplink/payUrl để mở app MoMo hoặc WebView",
               security = @SecurityRequirement(name = OpenApiConfig.JWT_SCHEME))
    @PostMapping("/deposit/create")
    public ResponseEntity<ApiResponse<MomoCreateOrderResponse>> createDepositOrder(
            @Valid @RequestBody MomoDepositRequest request) {
        MomoCreateOrderResponse result = momoService.createDepositOrder(request);
        return ResponseEntity.ok(ApiResponse.ok("Lệnh nạp tiền đã được tạo", result));
    }

    /**
     * IPN (Instant Payment Notification) – MoMo gọi sau khi user thanh toán.
     * Không yêu cầu JWT. Luôn trả HTTP 200 dù xử lý lỗi nội bộ
     * (theo yêu cầu của MoMo: phải respond 200 trong vòng 15s).
     */
    @Operation(summary = "[PUBLIC] MoMo IPN callback",
               description = "Endpoint MoMo gọi sau khi user thanh toán. Không cần JWT.")
    @PostMapping("/deposit/callback")
    public ResponseEntity<Map<String, Object>> handleDepositCallback(
            @RequestBody Map<String, String> params) {
        log.info("MoMo IPN callback received: orderId={}", params.get("orderId"));
        try {
            momoService.handleDepositCallback(params);
            return ResponseEntity.ok(Map.of("resultCode", 0, "message", "Thành công"));
        } catch (Exception e) {
            log.error("MoMo IPN processing error", e);
            // Vẫn trả 200 để MoMo không retry
            return ResponseEntity.ok(Map.of("resultCode", -1, "message", e.getMessage()));
        }
    }

    /**
     * Redirect URL – MoMo redirect browser/WebView về đây sau khi thanh toán.
     * Với mobile deeplink, MoMo mở lại app trực tiếp nên endpoint này ít dùng.
     */
    @Operation(summary = "[PUBLIC] MoMo payment return URL",
               description = "MoMo redirect về đây sau khi thanh toán web. Không cần JWT.")
    @GetMapping("/deposit/return")
    public ResponseEntity<Map<String, Object>> handleDepositReturn(
            @RequestParam Map<String, String> params) {
        String orderId    = params.get("orderId");
        String resultCode = params.get("resultCode");
        log.info("MoMo return URL hit: orderId={}, resultCode={}", orderId, resultCode);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId != null ? orderId : "",
                "resultCode", resultCode != null ? resultCode : "-1",
                "message", "0".equals(resultCode) ? "Thanh toán thành công" : "Thanh toán thất bại"
        ));
    }

    /**
     * Polling – Client gọi sau khi quay lại app để lấy trạng thái.
     */
    @Operation(summary = "Kiểm tra trạng thái lệnh nạp tiền",
               security = @SecurityRequirement(name = OpenApiConfig.JWT_SCHEME))
    @GetMapping("/deposit/{orderId}/status")
    public ResponseEntity<ApiResponse<MomoTransactionStatus>> getDepositStatus(
            @PathVariable String orderId) {
        MomoTransactionStatus status = momoService.getDepositStatus(orderId);
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    // ─── Rút tiền ────────────────────────────────────────────────────────────

    @Operation(summary = "Yêu cầu rút tiền về ví MoMo",
               description = "Rút tiền từ ví TaskHub về số điện thoại MoMo của user",
               security = @SecurityRequirement(name = OpenApiConfig.JWT_SCHEME))
    @PostMapping("/withdraw/request")
    public ResponseEntity<ApiResponse<MomoWithdrawResponse>> requestWithdrawal(
            @Valid @RequestBody MomoWithdrawRequest request) {
        MomoWithdrawResponse result = momoService.requestWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.ok("Yêu cầu rút tiền đã được gửi", result));
    }
}
