package com.taskhub.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustedDeviceServiceTest {
    private final TrustedDeviceService service = new TrustedDeviceService(
            "test-secret-that-is-long-enough-for-hmac-signing",
            "taskhub_trusted", true, "None", 2_592_000);

    @Test
    void rememberedCookieIsValidOnlyForItsUser() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        service.remember(response, 42L);
        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        String cookie = header.getValue();
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));

        assertTrue(service.isTrusted(token, 42L));
        assertFalse(service.isTrusted(token, 43L));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("Max-Age=2592000"));
    }

    @Test
    void rejectsTamperedAndMalformedTokens() {
        assertFalse(service.isTrusted("invalid", 42L));
        assertFalse(service.isTrusted("eA.eA", 42L));
        assertFalse(service.isTrusted(null, 42L));
    }
}
