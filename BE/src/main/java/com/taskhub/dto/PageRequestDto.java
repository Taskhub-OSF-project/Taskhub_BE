package com.taskhub.dto;

import lombok.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PageRequestDto {
    private int page = 0;
    private int size = 20;
    private String sortBy = "id";
    private String sortDir = "desc";

    public PageRequest toSpringPageRequest() {
        String resolvedSortBy = sortBy == null || sortBy.isBlank() ? "id" : sortBy.trim();
        String resolvedSortDir = sortDir == null || sortDir.isBlank() ? "desc" : sortDir.trim();
        Sort.Direction direction = "asc".equalsIgnoreCase(resolvedSortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, resolvedSortBy));
    }
}
