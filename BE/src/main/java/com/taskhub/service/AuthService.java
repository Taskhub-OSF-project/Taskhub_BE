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

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw TaskHubException.badRequest("Email already registered");

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(req.getRole())
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token).userId(user.getId()).email(user.getEmail())
                .fullName(user.getFullName()).role(user.getRole()).build();
    }

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
}
