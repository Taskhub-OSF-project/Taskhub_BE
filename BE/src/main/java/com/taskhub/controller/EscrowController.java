package com.taskhub.controller;

import com.taskhub.dto.response.ApiResponse;
import com.taskhub.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {
    private final EscrowService escrowService;

    @PostMapping("/fund/{taskId}")
    @PreAuthorize("hasRole('HIRER')")
    public ResponseEntity<ApiResponse<Void>> fund(@PathVariable Long taskId) {
        escrowService.fundEscrow(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Escrow funded", null));
    }

    @PostMapping("/release/{taskId}")
    @PreAuthorize("hasRole('HIRER')")
    public ResponseEntity<ApiResponse<Void>> release(@PathVariable Long taskId) {
        escrowService.releaseEscrow(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Escrow released", null));
    }

    @PostMapping("/refund/{taskId}")
    @PreAuthorize("hasRole('HIRER')")
    public ResponseEntity<ApiResponse<Void>> refund(@PathVariable Long taskId) {
        escrowService.refundEscrow(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Escrow refunded", null));
    }
}
