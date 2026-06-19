package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.FreelancerSearchResponse;
import com.taskhub.dto.response.PublicTaskResponse;
import com.taskhub.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @GetMapping("/freelancers")
    public ResponseEntity<ApiResponse<PageResponse<FreelancerSearchResponse>>> searchFreelancers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok("Freelancers retrieved",
                searchService.searchFreelancers(keyword, pageReq)));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<PageResponse<PublicTaskResponse>>> searchTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok("Tasks retrieved",
                searchService.searchTasks(keyword, category, null, pageReq)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Categories retrieved",
                searchService.getPopularCategories()));
    }
}
