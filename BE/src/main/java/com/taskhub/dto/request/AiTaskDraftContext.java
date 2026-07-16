package com.taskhub.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskDraftContext {
    @Size(max = 120)
    private String title;

    @Size(max = 5000)
    private String description;

    @Size(max = 100)
    private String category;

    @Size(max = 50)
    private String budget;

    @Size(max = 50)
    private String deadline;

    @Size(max = 8)
    private List<@Size(max = 1000) String> acceptanceCriteria;
}
