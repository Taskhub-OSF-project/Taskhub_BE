package com.taskhub.controller;

import com.taskhub.dto.response.ApiResponse;
import com.taskhub.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {
    private final EscrowService escrowService;

    @PostMapping("/fund/{taskId}")
    public ResponseEntity<ApiResponse<Void>> fund(@PathVariable UUID taskId) {
        escrowService.fundEscrow(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Escrow funded", null));
    }

    @PostMapping("/release/{taskId}")
    public ResponseEntity<ApiResponse<Void>> release(@PathVariable UUID taskId) {
        escrowService.releaseEscrow(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Escrow released", null));
    }
}
