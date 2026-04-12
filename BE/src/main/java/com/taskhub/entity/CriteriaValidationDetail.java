package com.taskhub.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor(force = true)
@Getter
public class CriteriaValidationDetail {
    private final int index;
    private final String criteria;
    private final boolean isValid;
    private final String issue;
    private final String suggestion;

}