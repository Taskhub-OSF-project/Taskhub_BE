package com.taskhub.controller;

import com.taskhub.dto.request.PortfolioItemRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.PortfolioItemResponse;
import com.taskhub.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<PortfolioItemResponse>>> getMyPortfolio() {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio retrieved", portfolioService.getMyPortfolio()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<PortfolioItemResponse>>> getPublicPortfolio(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio retrieved", portfolioService.getPublicPortfolio(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioItemResponse>> createItem(@Valid @RequestBody PortfolioItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio item created", portfolioService.createPortfolioItem(req)));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ApiResponse<PortfolioItemResponse>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody PortfolioItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio item updated", portfolioService.updatePortfolioItem(itemId, req)));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Long itemId) {
        portfolioService.deletePortfolioItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok("Portfolio item deleted", null));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderItems(@RequestBody List<Long> itemIds) {
        portfolioService.reorderPortfolioItems(itemIds);
        return ResponseEntity.ok(ApiResponse.ok("Portfolio reordered", null));
    }
}
