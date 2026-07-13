package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPricingResponse {

    private BigDecimal minPrice;
    private BigDecimal recommendedPrice;
    private BigDecimal maxPrice;

    @Builder.Default
    private String currency = "VND";

    private Double estimatedHours;
    private String estimatedDuration;

    private String difficultyLevel;

    private List<String> pricingFactors;

    private String marketAnalysis;

    private Double confidence;

    private LocalDateTime generatedAt;
}
