package com.taskhub.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tiện ích cho token bảo mật: sinh token ngẫu nhiên và băm SHA-256.
 * Token chỉ lưu dưới dạng hash trong DB; raw token không bao giờ lưu.
 */
public final class TokenHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHasher() {}

    /** Sinh token ngẫu nhiên URL-safe (256-bit entropy). */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Băm SHA-256 -> hex (64 ký tự), khớp với cột tokenHash length=64. */
    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
