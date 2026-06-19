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
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));
    }
}
