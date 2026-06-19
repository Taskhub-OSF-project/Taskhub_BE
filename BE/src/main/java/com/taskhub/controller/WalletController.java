package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.*;
import com.taskhub.config.OpenApiConfig;
import com.taskhub.service.WalletService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Requires JWT — use Authorize button first")
@SecurityRequirement(name = OpenApiConfig.JWT_SCHEME)
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> balance() {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getBalance()));
    }

    @GetMapping("/readiness/create-task")
    public ResponseEntity<ApiResponse<WalletReadinessResponse>> createTaskReadiness(
            @RequestParam BigDecimal budget) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.assessCreateTaskReadiness(budget)));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<WalletResponse>> deposit(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Deposit successful", walletService.deposit(amount)));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<WalletResponse>> withdraw(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Withdraw successful", walletService.withdraw(amount)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> transactions() {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getTransactions()));
    }

    @GetMapping("/transactions/paged")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> transactionsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(walletService.getTransactionsPaged(pageReq)));
    }
}
