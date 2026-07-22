package com.taskhub.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.taskhub.exception.TaskHubException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GoogleIdentityService {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityService(@Value("${app.auth.google-client-id:}") String clientId) {
        String normalizedClientId = clientId == null ? "" : clientId.trim();
        this.verifier = normalizedClientId.isBlank() ? null : new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(List.of(normalizedClientId))
                .build();
    }

    public GoogleIdentity verify(String credential) {
        if (verifier == null) {
            throw TaskHubException.internalError("Đăng nhập Google chưa được cấu hình");
        }
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null) throw TaskHubException.unauthorized("Phiên Google không hợp lệ hoặc đã hết hạn");
            GoogleIdToken.Payload payload = token.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            boolean googleAuthoritative = email != null && (email.toLowerCase().endsWith("@gmail.com")
                    || (payload.getHostedDomain() != null && !payload.getHostedDomain().isBlank()));
            if (!emailVerified || !googleAuthoritative) {
                throw TaskHubException.unauthorized("Google chưa xác minh quyền sở hữu email này");
            }
            return new GoogleIdentity(
                    payload.getSubject(), email, value(payload.get("name")),
                    value(payload.get("picture")));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw TaskHubException.unauthorized("Không thể xác minh phiên đăng nhập Google");
        }
    }

    private String value(Object input) {
        return input == null ? null : input.toString().trim();
    }

    public record GoogleIdentity(String subject, String email, String fullName, String pictureUrl) {}
}
