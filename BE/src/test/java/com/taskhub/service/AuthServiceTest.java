package com.taskhub.service;

import com.taskhub.dto.request.ChangePasswordRequest;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AuthServiceTest {
    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void changePassword_revokesRefreshTokens() {
        User user = userRepository.save(User.builder()
                .email("changepw@test.com")
                .password(passwordEncoder.encode("oldpassword"))
                .fullName("Change PW")
                .role(Role.STUDENT)
                .build());

        var login = authService.login(LoginRequest.builder()
                .email("changepw@test.com")
                .password("oldpassword")
                .build());
        assertFalse(refreshTokenRepository.findAll().isEmpty());

        userService.changePassword(user.getId(), ChangePasswordRequest.builder()
                .currentPassword("oldpassword")
                .newPassword("newpassword1")
                .build());

        assertTrue(refreshTokenRepository.findAll().stream().allMatch(t -> t.isRevoked()));
        assertDoesNotThrow(() -> authService.login(LoginRequest.builder()
                .email("changepw@test.com")
                .password("newpassword1")
                .build()));
    }

    @Test
    void login_invalidPassword_throwsBadRequest() {
        userRepository.save(User.builder()
                .email("badlogin@test.com")
                .password(passwordEncoder.encode("correct"))
                .fullName("Bad Login")
                .role(Role.STUDENT)
                .build());

        TaskHubException ex = assertThrows(TaskHubException.class, () ->
                authService.login(LoginRequest.builder()
                        .email("badlogin@test.com")
                        .password("wrong")
                        .build()));
        assertEquals(400, ex.getStatus().value());
    }
}
