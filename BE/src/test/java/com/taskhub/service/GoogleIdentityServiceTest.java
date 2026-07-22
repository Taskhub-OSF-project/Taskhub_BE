package com.taskhub.service;

import com.taskhub.exception.TaskHubException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdentityServiceTest {

    @Test
    void missingClientId_reportsConfigurationError() {
        GoogleIdentityService service = new GoogleIdentityService(" ");

        TaskHubException error = assertThrows(TaskHubException.class,
                () -> service.verify("invalid-test-token"));

        assertEquals(500, error.getStatus().value());
        assertEquals("Đăng nhập Google chưa được cấu hình", error.getMessage());
    }

    @Test
    void malformedCredential_isRejectedAsUnauthorized() {
        GoogleIdentityService service = new GoogleIdentityService(
                "547513175107-8ontkvmfur1r9giot2d98o1lufeiv1lm.apps.googleusercontent.com");

        TaskHubException error = assertThrows(TaskHubException.class,
                () -> service.verify("invalid-test-token"));

        assertEquals(401, error.getStatus().value());
        assertEquals("Không thể xác minh phiên đăng nhập Google", error.getMessage());
    }
}
