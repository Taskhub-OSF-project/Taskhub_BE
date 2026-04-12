package com.taskhub.controller;

import com.taskhub.dto.response.*;
import com.taskhub.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> balance() {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getBalance()));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<WalletResponse>> deposit(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Deposit successful", walletService.deposit(amount)));
    }
}
