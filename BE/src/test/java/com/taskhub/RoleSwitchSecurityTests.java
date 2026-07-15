package com.taskhub;

import com.taskhub.entity.RefreshToken;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.UserService;
import com.taskhub.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RoleSwitchSecurityTests {
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void activeOwnedTaskBlocksRoleSwitch() {
        User hirer = user("role-owner@example.com", Role.HIRER);
        taskRepository.save(task(hirer, null, TaskStatus.DRAFT));

        assertThrows(TaskHubException.class,
                () -> userService.switchRoleAndReturnToken(hirer.getId()));
    }

    @Test
    void activeAssignedTaskBlocksRoleSwitch() {
        User hirer = user("role-assigned-hirer@example.com", Role.HIRER);
        User student = user("role-assigned-student@example.com", Role.STUDENT);
        taskRepository.save(task(hirer, student, TaskStatus.IN_PROGRESS));

        assertThrows(TaskHubException.class,
                () -> userService.switchRoleAndReturnToken(student.getId()));
    }

    @Test
    void successfulSwitchRotatesRefreshTokens() {
        User student = user("role-success@example.com", Role.STUDENT);
        RefreshToken old = refreshTokenRepository.save(RefreshToken.builder()
                .userId(student.getId())
                .tokenHash(TokenHasher.sha256("old-refresh"))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());

        var response = userService.switchRoleAndReturnToken(student.getId());

        assertEquals(Role.HIRER, response.getRole());
        assertNotNull(response.getToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(refreshTokenRepository.findById(old.getId()).orElseThrow().isRevoked());
        assertTrue(refreshTokenRepository.findAll().stream()
                .anyMatch(token -> !token.isRevoked()
                        && token.getTokenHash().equals(TokenHasher.sha256(response.getRefreshToken()))));
    }

    @Test
    void banningUserRevokesEveryRefreshToken() {
        User student = user("role-ban@example.com", Role.STUDENT);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(student.getId())
                .tokenHash(TokenHasher.sha256("ban-refresh"))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());

        userService.setUserBanned(student.getId(), true);

        assertTrue(userRepository.findById(student.getId()).orElseThrow().getIsBanned());
        assertTrue(refreshTokenRepository.findAll().stream().allMatch(RefreshToken::isRevoked));
    }

    private User user(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password("encoded")
                .fullName("Role Test")
                .role(role)
                .build());
    }

    private Task task(User hirer, User assigned, TaskStatus status) {
        return Task.builder()
                .title("Role obligation")
                .description("Role switching must preserve active obligations")
                .budget(new BigDecimal("1000.00"))
                .deadline(LocalDateTime.now().plusDays(2))
                .status(status)
                .hirer(hirer)
                .assignedTo(assigned)
                .build();
    }
}
