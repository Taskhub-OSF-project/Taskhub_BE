package com.taskhub.service;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.entity.User;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service xử lý nghiệp vụ đăng ký/đăng nhập.
 * Thuộc module Auth, được gọi từ AuthController.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Đăng ký tài khoản và trả JWT.
     * Input: RegisterRequest.
     * Output: AuthResponse.
     */
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw TaskHubException.badRequest("Email already registered");

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .university(trimToNull(req.getUniversity()))
                .major(trimToNull(req.getMajor()))
                .role(req.getRole())
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token).userId(user.getId()).email(user.getEmail())
                .fullName(user.getFullName()).role(user.getRole()).build();
    }

    /**
     * Đăng nhập và trả JWT nếu thông tin hợp lệ.
     * Input: LoginRequest.
     * Output: AuthResponse.
     */
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> TaskHubException.badRequest("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw TaskHubException.badRequest("Invalid credentials");

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token).userId(user.getId()).email(user.getEmail())
                .fullName(user.getFullName()).role(user.getRole()).build();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}