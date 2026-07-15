package com.taskhub.dto;

import lombok.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PageRequestDto {
    private static final java.util.Set<String> SAFE_SORT_FIELDS = java.util.Set.of(
            "id", "createdAt", "updatedAt", "deadline", "budget", "title", "status");

    private int page = 0;
    private int size = 20;
    private String sortBy = "id";
    private String sortDir = "desc";

    public int getPage() {
        return Math.max(page, 0);
    }

    public int getSize() {
        return Math.min(Math.max(size, 1), 100);
    }

    public String getSortBy() {
        String candidate = sortBy == null ? "" : sortBy.trim();
        return SAFE_SORT_FIELDS.contains(candidate) ? candidate : "id";
    }

    public String getSortDir() {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    public PageRequest toSpringPageRequest() {
        Sort.Direction direction = "asc".equalsIgnoreCase(getSortDir())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(getPage(), getSize(), Sort.by(direction, getSortBy()));
    }
}
