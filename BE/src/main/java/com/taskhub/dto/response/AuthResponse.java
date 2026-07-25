package com.taskhub.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taskhub.enums.Role;
import lombok.*;
import java.time.Instant;
import java.util.Set;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    /** Access token ngắn hạn — gửi kèm header Authorization: Bearer. */
    private String token;
    /** Refresh token dài hạn — dùng cho POST /api/auth/refresh. */
    private String refreshToken;
    /** Loại token, luôn là "Bearer". */
    @Builder.Default
    private String tokenType = "Bearer";
    /** Thời gian sống còn lại của access token (giây). */
    private long expiresIn;
    private Long userId;
    private String email;
    private String fullName;
    private Role role;
    private Set<Role> roles;
    private boolean emailVerified;
    private boolean verificationRequired;
    private boolean emailOtpRequired;
    private String otpChallengeId;
    private String otpPurpose;
    private long otpExpiresIn;
    /** Unix timestamp (seconds) when access token expires */
    private Long expiresAt;
    /** Internal controller signal; never serialized to clients. */
    @JsonIgnore
    private boolean trustedDeviceGranted;
}
