package com.taskhub.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Cập nhật profile của chính mình. Các field null = không đổi.
 * Email/role/ví không cho sửa qua endpoint này.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 255)
    private String fullName;

    @Size(max = 100)
    private String university;

    @Size(max = 100)
    private String major;
}
