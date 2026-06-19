package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.CreateReviewRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.ReviewResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Review submitted", reviewService.createReview(taskId, req)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getUserReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReviewsForUser(userId, pageReq)));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getUserProfile(userId)));
    }
}
