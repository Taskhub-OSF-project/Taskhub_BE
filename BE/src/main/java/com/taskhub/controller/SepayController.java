package com.taskhub.controller;

import com.taskhub.config.SepayProperties;
import com.taskhub.dto.request.SepayWebhookRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.SepayBankConfigResponse;
import com.taskhub.service.SepayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/sepay")
@RequiredArgsConstructor
@Tag(name = "SePay Payment", description = "Tích hợp cổng chuyển khoản tự động SePay (VietQR)")
public class SepayController {

    private final SepayService sepayService;
    private final SepayProperties sepayProperties;

    @GetMapping("/config")
    @Operation(summary = "Lấy thông tin tài khoản ngân hàng và tham số cấu hình VietQR của hệ thống")
    public ResponseEntity<ApiResponse<SepayBankConfigResponse>> getBankConfig() {
        SepayBankConfigResponse response = SepayBankConfigResponse.builder()
                .bankCode(sepayProperties.getBankCode())
                .bankAccount(sepayProperties.getBankAccount())
                .bankName(sepayProperties.getBankName())
                .accountName(sepayProperties.getAccountName())
                .qrTemplate(sepayProperties.getQrTemplate())
                .minDepositAmount(sepayProperties.getMinDepositAmount())
                .maxDepositAmount(sepayProperties.getMaxDepositAmount())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Retrieved SePay bank configuration", response));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook nhận dữ liệu thanh toán chuyển khoản từ SePay")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody SepayWebhookRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "API-Key", required = false) String apiKeyHeader) {
        
        String token = apiKeyHeader != null ? apiKeyHeader : authorizationHeader;
        sepayService.processWebhook(request, token);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully", null));
    }
}
