package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionSuggestionResponse {
    private int index;
    private String criteria;
    private String status;
    private String suggestion;
}
