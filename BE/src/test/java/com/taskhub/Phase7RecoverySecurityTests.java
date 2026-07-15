package com.taskhub;

import com.taskhub.dto.request.ForgotPasswordRequest;
import com.taskhub.dto.request.PasswordResetConfirmRequest;
import com.taskhub.dto.request.RecoverAccountRequest;
import com.taskhub.entity.OtpToken;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.OtpTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.VerificationTokenRepository;
import com.taskhub.service.AuthService;
import com.taskhub.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Phase7RecoverySecurityTests {
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private OtpTokenRepository otpTokenRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;

    @Test
    void accountRecoveryDoesNotRevealWhetherContactExists() {
        user("phase7-existing@example.com", "0901234567");
        Map<String, Object> existing = authService.recoverAccount(RecoverAccountRequest.builder()
                .channel("EMAIL").contact("phase7-existing@example.com").build());
        Map<String, Object> missing = authService.recoverAccount(RecoverAccountRequest.builder()
                .channel("EMAIL").contact("phase7-missing@example.com").build());

        assertEquals(existing, missing);
        assertFalse(existing.containsKey("maskedEmail"));
    }

    @Test
    void disabledMailProviderDoesNotCreateUndeliverableResetToken() {
        user("phase7-mail-disabled@example.com", "0901111111");
        long before = verificationTokenRepository.count();

        authService.forgotPassword(ForgotPasswordRequest.builder()
                .email("phase7-mail-disabled@example.com").build());

        assertEquals(before, verificationTokenRepository.count());
    }

    @Test
    void otpIsHashedAndLockedAfterFiveWrongAttempts() {
        user("phase7-otp@example.com", "0902222222");
        OtpToken otp = otpTokenRepository.save(OtpToken.builder()
                .phone("0902222222")
                .code(TokenHasher.sha256("123456"))
                .type("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build());

        for (int i = 0; i < 5; i++) {
            assertThrows(TaskHubException.class, () -> authService.confirmPasswordReset(
                    PasswordResetConfirmRequest.builder()
                            .identifier("0902222222")
                            .code("000000")
                            .newPassword("NewPassword123!")
                            .build()));
        }

        OtpToken locked = otpTokenRepository.findById(otp.getId()).orElseThrow();
        assertEquals(5, locked.getFailedAttempts());
        assertTrue(locked.getUsed());
        assertEquals(64, locked.getCode().length());
    }

    @Test
    void validOtpCanBeUsedOnlyOnce() {
        User user = user("phase7-valid@example.com", "0903333333");
        OtpToken otp = otpTokenRepository.save(OtpToken.builder()
                .phone("0903333333")
                .code(TokenHasher.sha256("654321"))
                .type("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build());
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .identifier("0903333333")
                .code("654321")
                .newPassword("NewPassword123!")
                .build();

        authService.confirmPasswordReset(request);

        assertTrue(passwordEncoder.matches("NewPassword123!",
                userRepository.findById(user.getId()).orElseThrow().getPassword()));
        assertTrue(otpTokenRepository.findById(otp.getId()).orElseThrow().getUsed());
        assertThrows(TaskHubException.class, () -> authService.confirmPasswordReset(request));
    }

    @Test
    void invalidRecoveryChannelIsRejected() {
        assertThrows(TaskHubException.class, () -> authService.recoverAccount(
                RecoverAccountRequest.builder().channel("TELEGRAM").contact("someone").build()));
    }

    @Test
    void healthEndpointChecksDatabase() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private User user(String email, String phone) {
        return userRepository.save(User.builder()
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode("OldPassword123!"))
                .fullName("Phase 7 User")
                .role(Role.STUDENT)
                .build());
    }
}
